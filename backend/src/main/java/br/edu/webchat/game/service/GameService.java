package br.edu.webchat.game.service;

import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.service.ChatService;
import br.edu.webchat.game.dto.ClockResponse;
import br.edu.webchat.game.dto.CreateGameRequest;
import br.edu.webchat.game.dto.GameResponse;
import br.edu.webchat.game.dto.PlayerResponse;
import br.edu.webchat.game.engine.GameEngine;
import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.game.entity.Game;
import br.edu.webchat.game.entity.GameResult;
import br.edu.webchat.game.entity.GameStatus;
import br.edu.webchat.game.entity.GameType;
import br.edu.webchat.game.repository.GameRepository;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Partidas entre dois colaboradores. O servidor e a autoridade: o navegador so envia a
 * jogada, e aqui se confere quem joga, de quem e a vez e, pelo motor do jogo, se a jogada
 * vale e se a partida terminou. Cada mudanca e salva e enviada aos dois jogadores.
 */
@Service
public class GameService {

	/** Quantas partidas terminadas a lista devolve, alem de todas as em andamento. */
	static final int FINISHED_GAMES_LISTED = 20;

	/** Ritmos aceitos nos jogos com relogio, em minutos para cada jogador. */
	static final Set<Integer> TIME_CONTROLS_MINUTES = Set.of(3, 5, 10);

	private static final Set<GameStatus> ACTIVE = Set.of(GameStatus.INVITED, GameStatus.IN_PROGRESS);

	private final GameRepository gameRepository;
	private final UserRepository userRepository;
	private final ChatRepository chatRepository;
	private final ChatService chatService;
	private final ApplicationEventPublisher events;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final Map<GameType, GameEngine<?>> engines = new EnumMap<>(GameType.class);

	public GameService(GameRepository gameRepository, UserRepository userRepository, ChatRepository chatRepository,
			ChatService chatService, ApplicationEventPublisher events, ObjectMapper objectMapper, Clock clock,
			List<GameEngine<?>> engines) {
		this.gameRepository = gameRepository;
		this.userRepository = userRepository;
		this.chatRepository = chatRepository;
		this.chatService = chatService;
		this.events = events;
		this.objectMapper = objectMapper;
		this.clock = clock;
		engines.forEach(engine -> this.engines.put(engine.type(), engine));
	}

	@Transactional
	public GameResponse create(String authenticatedEmail, CreateGameRequest request) {
		User me = findUser(authenticatedEmail);
		GameEngine<?> engine = engineOf(request.type());
		Duration timeControl = timeControlOf(request);

		if (me.getId().equals(request.opponentId())) {
			throw new BadRequestException("Nao e possivel desafiar a si mesmo");
		}
		User opponent = userRepository.findById(request.opponentId())
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + request.opponentId()));

		if (request.chatId() != null) {
			requireBothInChat(request.chatId(), authenticatedEmail, opponent);
		}
		if (gameRepository.existsBetween(request.type(), me.getId(), opponent.getId(), ACTIVE)) {
			throw new ConflictException("Ja existe uma partida desse jogo em aberto com essa pessoa");
		}

		Game game = new Game(request.type(), me, opponent,
				request.chatId() == null ? null : chatRepository.getReferenceById(request.chatId()),
				write(engine.initialState()));
		if (timeControl != null) {
			game.useTimeControl(timeControl);
		}
		gameRepository.saveAndFlush(game);

		return publish(game);
	}

	@Transactional(readOnly = true)
	public List<GameResponse> findMyGames(String authenticatedEmail) {
		User me = findUser(authenticatedEmail);

		List<Game> games = new ArrayList<>(gameRepository.findByPlayer(me.getId(), ACTIVE, Pageable.unpaged()));
		games.addAll(gameRepository.findByPlayer(me.getId(), Set.of(GameStatus.FINISHED),
				PageRequest.of(0, FINISHED_GAMES_LISTED)));

		return games.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public GameResponse findById(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		playerIndex(game, findUser(authenticatedEmail));
		return toResponse(game);
	}

	@Transactional
	public GameResponse accept(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		requireInvitation(game);
		if (playerIndex(game, findUser(authenticatedEmail)) != Game.OPPONENT) {
			throw new BadRequestException("So quem foi desafiado pode aceitar");
		}

		game.start();
		game.startTurn(playerToMove(game, engineOf(game.getType())), clock.instant());
		return publish(game);
	}

	@Transactional
	public GameResponse decline(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		requireInvitation(game);
		if (playerIndex(game, findUser(authenticatedEmail)) != Game.OPPONENT) {
			throw new BadRequestException("So quem foi desafiado pode recusar; para desistir do desafio, cancele");
		}

		game.finish(GameResult.DECLINED, null);
		return publish(game);
	}

	@Transactional
	public GameResponse cancel(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		requireInvitation(game);
		if (playerIndex(game, findUser(authenticatedEmail)) != Game.CHALLENGER) {
			throw new BadRequestException("So quem desafiou pode cancelar o desafio");
		}

		game.finish(GameResult.CANCELED, null);
		return publish(game);
	}

	@Transactional
	public GameResponse resign(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		int player = playerIndex(game, findUser(authenticatedEmail));
		requireInProgress(game);

		stopClock(game, clock.instant());
		game.finish(GameResult.RESIGNATION, game.player(1 - player));
		return publish(game);
	}

	/**
	 * Confere o relogio antes de aplicar a jogada: se o tempo de quem jogou ja tinha acabado,
	 * a jogada nao vale e a partida termina por tempo. Jogar tambem recusa uma proposta de
	 * empate pendente.
	 */
	@Transactional
	public GameResponse move(Long gameId, String authenticatedEmail, JsonNode move) {
		Game game = findGame(gameId);
		int player = playerIndex(game, findUser(authenticatedEmail));
		requireInProgress(game);
		GameEngine<?> engine = engineOf(game.getType());
		if (playerToMove(game, engine) != player) {
			throw new BadRequestException("Nao e a sua vez");
		}

		Instant now = clock.instant();
		if (!game.endTurn(player, now)) {
			game.finish(GameResult.TIMEOUT, game.player(1 - player));
			return publish(game);
		}

		play(game, engine, player, move);
		game.clearDrawOffer();
		if (game.getStatus() == GameStatus.IN_PROGRESS) {
			game.startTurn(playerToMove(game, engine), now);
		}
		return publish(game);
	}

	/** Propoe empate ao adversario. Se ele ja tinha proposto, os dois querem: a partida empata. */
	@Transactional
	public GameResponse offerDraw(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		User me = findUser(authenticatedEmail);
		int player = playerIndex(game, me);
		requireInProgress(game);
		requireDrawOffers(game);

		Long offeredBy = game.getDrawOfferedById();
		if (offeredBy != null && offeredBy.equals(game.player(1 - player).getId())) {
			stopClock(game, clock.instant());
			game.finish(GameResult.AGREED_DRAW, null);
		}
		else {
			game.offerDraw(me);
		}
		return publish(game);
	}

	@Transactional
	public GameResponse acceptDraw(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		int player = playerIndex(game, findUser(authenticatedEmail));
		requireInProgress(game);
		requireDrawOfferFromOpponent(game, player);

		stopClock(game, clock.instant());
		game.finish(GameResult.AGREED_DRAW, null);
		return publish(game);
	}

	@Transactional
	public GameResponse declineDraw(Long gameId, String authenticatedEmail) {
		Game game = findGame(gameId);
		int player = playerIndex(game, findUser(authenticatedEmail));
		requireInProgress(game);
		requireDrawOfferFromOpponent(game, player);

		game.clearDrawOffer();
		return publish(game);
	}

	@Transactional(readOnly = true)
	public List<Long> findOutOfTime(Instant now) {
		return gameRepository.findOutOfTime(now);
	}

	/** Encerra a partida por tempo se o jogador da vez ficou sem tempo ate {@code now}. */
	@Transactional
	public void expireIfOutOfTime(Long gameId, Instant now) {
		Game game = findGame(gameId);
		if (game.getStatus() != GameStatus.IN_PROGRESS || !game.isOutOfTime(now)) {
			return;
		}

		int player = playerToMove(game, engineOf(game.getType()));
		game.endTurn(player, now);
		game.finish(GameResult.TIMEOUT, game.player(1 - player));
		publish(game);
	}

	private <S> void play(Game game, GameEngine<S> engine, int player, JsonNode move) {
		S state = read(game, engine);
		if (engine.playerToMove(state) != player) {
			throw new BadRequestException("Nao e a sua vez");
		}

		S next = engine.applyMove(state, player, move);
		game.changeState(write(next));

		GameOutcome outcome = engine.outcome(next);
		if (outcome.finished()) {
			game.finish(outcome.winner() == null ? GameResult.DRAW : GameResult.WIN,
					outcome.winner() == null ? null : game.player(outcome.winner()));
		}
	}

	/** Para o relogio descontando a vez em andamento, para o tempo final ficar certo na tela. */
	private void stopClock(Game game, Instant now) {
		game.endTurn(playerToMove(game, engineOf(game.getType())), now);
	}

	private static Duration timeControlOf(CreateGameRequest request) {
		Integer minutes = request.timeControlMinutes();
		if (!request.type().isTimed()) {
			if (minutes != null) {
				throw new BadRequestException("Este jogo nao tem relogio");
			}
			return null;
		}
		if (minutes == null || !TIME_CONTROLS_MINUTES.contains(minutes)) {
			throw new BadRequestException("Escolha o ritmo da partida: 3, 5 ou 10 minutos para cada jogador");
		}
		return Duration.ofMinutes(minutes);
	}

	private static void requireDrawOffers(Game game) {
		if (!game.getType().isTimed()) {
			throw new BadRequestException("Este jogo nao aceita proposta de empate");
		}
	}

	private static void requireDrawOfferFromOpponent(Game game, int player) {
		Long offeredBy = game.getDrawOfferedById();
		if (offeredBy == null || !offeredBy.equals(game.player(1 - player).getId())) {
			throw new BadRequestException("Nao ha proposta de empate do adversario");
		}
	}

	private void requireBothInChat(Long chatId, String authenticatedEmail, User opponent) {
		ChatResponse chat = chatService.findById(chatId, authenticatedEmail);
		boolean opponentInChat = chat.participants().stream()
				.anyMatch(participant -> participant.id().equals(opponent.getId()));
		if (!opponentInChat) {
			throw new BadRequestException("O adversario nao participa desta conversa");
		}
	}

	private static void requireInvitation(Game game) {
		if (game.getStatus() != GameStatus.INVITED) {
			throw new BadRequestException("Este desafio nao esta mais aguardando resposta");
		}
	}

	private static void requireInProgress(Game game) {
		if (game.getStatus() != GameStatus.IN_PROGRESS) {
			throw new BadRequestException("A partida nao esta em andamento");
		}
	}

	/** Indice do usuario na partida; quem nao joga nao ve nem mexe. */
	private static int playerIndex(Game game, User user) {
		int index = game.playerIndexOf(user.getId());
		if (index < 0) {
			throw new ForbiddenException("Voce nao participa desta partida");
		}
		return index;
	}

	/**
	 * O flush vem antes da resposta para que ela leve o {@code updatedAt} novo (o frontend
	 * usa esse campo para descartar eventos atrasados) e para que um conflito de versao
	 * apareca aqui, e nao depois de o evento ja ter sido montado.
	 */
	private GameResponse publish(Game game) {
		gameRepository.flush();
		GameResponse response = toResponse(game);
		List<String> emails = List.of(game.getChallenger().getEmail(), game.getOpponent().getEmail());
		events.publishEvent(new GameChangedEvent(response, emails));
		return response;
	}

	private GameResponse toResponse(Game game) {
		int toMove = game.getStatus() == GameStatus.IN_PROGRESS ? playerToMove(game, engineOf(game.getType())) : -1;
		Long turnPlayerId = toMove >= 0 ? game.player(toMove).getId() : null;

		return new GameResponse(
				game.getId(),
				game.getType(),
				game.getStatus(),
				game.getResult(),
				game.getChatId(),
				PlayerResponse.from(game.getChallenger()),
				PlayerResponse.from(game.getOpponent()),
				turnPlayerId,
				game.getWinner() == null ? null : game.getWinner().getId(),
				readTree(game.getState()),
				clockOf(game, toMove),
				game.getDrawOfferedById(),
				game.getCreatedAt(),
				game.getUpdatedAt(),
				game.getFinishedAt());
	}

	private ClockResponse clockOf(Game game, int toMove) {
		if (!game.isTimed()) {
			return null;
		}
		Instant now = clock.instant();
		Long running = game.getTurnStartedAt() != null && toMove >= 0 ? game.player(toMove).getId() : null;
		return new ClockResponse(game.getTimeControlMs(),
				game.timeLeft(Game.CHALLENGER, toMove, now),
				game.timeLeft(Game.OPPONENT, toMove, now),
				running);
	}

	private <S> int playerToMove(Game game, GameEngine<S> engine) {
		return engine.playerToMove(read(game, engine));
	}

	private GameEngine<?> engineOf(GameType type) {
		GameEngine<?> engine = engines.get(type);
		if (engine == null) {
			throw new BadRequestException("Jogo ainda nao disponivel: " + type);
		}
		return engine;
	}

	private Game findGame(Long gameId) {
		return gameRepository.findById(gameId)
				.orElseThrow(() -> new NotFoundException("Partida nao encontrada: " + gameId));
	}

	private User findUser(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + email));
	}

	private <S> S read(Game game, GameEngine<S> engine) {
		try {
			return objectMapper.readValue(game.getState(), engine.stateType());
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Estado invalido na partida " + game.getId(), ex);
		}
	}

	private JsonNode readTree(String json) {
		try {
			return objectMapper.readTree(json);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Estado de partida invalido", ex);
		}
	}

	private String write(Object state) {
		try {
			return objectMapper.writeValueAsString(state);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Nao foi possivel gravar o estado da partida", ex);
		}
	}

}
