package br.edu.webchat.game;

import br.edu.webchat.game.dto.GameResponse;
import br.edu.webchat.game.entity.GameResult;
import br.edu.webchat.game.entity.GameStatus;
import br.edu.webchat.game.entity.GameType;
import br.edu.webchat.game.service.GameService;
import br.edu.webchat.support.StompIntegrationTest;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Partidas com servidor, REST, WebSocket, seguranca e banco reais. */
class GameIntegrationTest extends StompIntegrationTest {

	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String MARIA = "maria@email.com";
	private static final String JOAO = "joao@email.com";
	private static final String GAMES = "/user/queue/games";

	@Autowired
	private UserService userService;

	@Autowired
	private GameService gameService;

	private Long alexandreId;
	private Long mariaId;
	private Long joaoId;

	@BeforeEach
	void setUp() {
		limparBase();
		alexandreId = userService.create(new CreateUserRequest("Alexandre", ALEXANDRE, "123456")).id();
		mariaId = userService.create(new CreateUserRequest("Maria", MARIA, "123456")).id();
		joaoId = userService.create(new CreateUserRequest("Joao", JOAO, "123456")).id();
	}

	@Test
	void partidaCompletaDoDesafioAVitoriaChegaAosDoisEmTempoReal() throws Exception {
		StompSession maria = conectar(MARIA);
		BlockingQueue<GameResponse> eventosMaria = subscribe(maria, MARIA, GAMES, GameResponse.class);
		StompSession alexandre = conectar(ALEXANDRE);
		BlockingQueue<GameResponse> eventosAlexandre = subscribe(alexandre, ALEXANDRE, GAMES, GameResponse.class);

		ResponseEntity<GameResponse> criada = rest.exchange(url("/api/v1/games"), HttpMethod.POST,
				json(ALEXANDRE, desafio(mariaId, null)), GameResponse.class);
		assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(criada.getHeaders().getLocation()).isNotNull();
		GameResponse desafio = criada.getBody();
		assertThat(desafio.status()).isEqualTo(GameStatus.INVITED);
		assertThat(desafio.challenger().id()).isEqualTo(alexandreId);
		assertThat(desafio.opponent().id()).isEqualTo(mariaId);
		assertThat(desafio.turnPlayerId()).isNull();
		assertThat(desafio.state().get("board").asText()).isEqualTo(".........");
		assertThat(eventosMaria.poll(5, TimeUnit.SECONDS).status()).isEqualTo(GameStatus.INVITED);
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS).status()).isEqualTo(GameStatus.INVITED);

		Long id = desafio.id();
		GameResponse aceita = acao(MARIA, id, "accept");
		assertThat(aceita.status()).isEqualTo(GameStatus.IN_PROGRESS);
		assertThat(aceita.updatedAt()).isAfter(desafio.updatedAt());
		assertThat(aceita.turnPlayerId()).isEqualTo(alexandreId);
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS).status()).isEqualTo(GameStatus.IN_PROGRESS);
		eventosMaria.poll(5, TimeUnit.SECONDS);

		// X: 0, 1, 2 (linha de cima); O: 3, 4
		jogar(ALEXANDRE, id, 0);
		jogar(MARIA, id, 3);
		jogar(ALEXANDRE, id, 1);
		jogar(MARIA, id, 4);
		GameResponse fim = jogar(ALEXANDRE, id, 2);

		assertThat(fim.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(fim.result()).isEqualTo(GameResult.WIN);
		assertThat(fim.winnerId()).isEqualTo(alexandreId);
		assertThat(fim.turnPlayerId()).isNull();
		assertThat(fim.finishedAt()).isNotNull();
		assertThat(fim.state().get("board").asText()).isEqualTo("XXXOO....");

		GameResponse ultimoDaMaria = ultimo(eventosMaria, 5);
		assertThat(ultimoDaMaria.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(ultimoDaMaria.winnerId()).isEqualTo(alexandreId);
	}

	@Test
	void naoJogaForaDaVezNemEmCasaOcupadaNemDepoisDoFim() {
		Long id = partidaEmAndamento();

		assertErro(() -> jogar(MARIA, id, 0), HttpStatus.BAD_REQUEST, "vez");
		jogar(ALEXANDRE, id, 0);
		assertErro(() -> jogar(MARIA, id, 0), HttpStatus.BAD_REQUEST, "ocupada");
		assertErro(() -> rest.exchange(url("/api/v1/games/" + id + "/moves"), HttpMethod.POST,
				json(MARIA, Map.of("cell", 42)), GameResponse.class), HttpStatus.BAD_REQUEST, "casa");

		acao(MARIA, id, "resign");
		assertErro(() -> jogar(ALEXANDRE, id, 1), HttpStatus.BAD_REQUEST, "andamento");
	}

	@Test
	void desistirDaAVitoriaAoOutro() {
		Long id = partidaEmAndamento();

		GameResponse fim = acao(ALEXANDRE, id, "resign");

		assertThat(fim.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(fim.result()).isEqualTo(GameResult.RESIGNATION);
		assertThat(fim.winnerId()).isEqualTo(mariaId);
	}

	@Test
	void soODesafiadoAceitaOuRecusaESoQuemDesafiouCancela() {
		Long id = criar(ALEXANDRE, mariaId, null).id();

		assertErro(() -> acao(ALEXANDRE, id, "accept"), HttpStatus.BAD_REQUEST, "desafiado");
		assertErro(() -> acao(ALEXANDRE, id, "decline"), HttpStatus.BAD_REQUEST, "desafiado");
		assertErro(() -> acao(MARIA, id, "cancel"), HttpStatus.BAD_REQUEST, "desafiou");
		assertErro(() -> jogar(ALEXANDRE, id, 0), HttpStatus.BAD_REQUEST, "andamento");

		GameResponse recusada = acao(MARIA, id, "decline");
		assertThat(recusada.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(recusada.result()).isEqualTo(GameResult.DECLINED);
		assertErro(() -> acao(MARIA, id, "accept"), HttpStatus.BAD_REQUEST, "aguardando");

		GameResponse cancelada = acao(ALEXANDRE, criar(ALEXANDRE, mariaId, null).id(), "cancel");
		assertThat(cancelada.result()).isEqualTo(GameResult.CANCELED);
	}

	@Test
	void quemNaoJogaNaoVeNemMexeNaPartida() {
		Long id = partidaEmAndamento();

		assertErro(() -> rest.exchange(url("/api/v1/games/" + id), HttpMethod.GET, json(JOAO, null), GameResponse.class),
				HttpStatus.FORBIDDEN, "participa");
		assertErro(() -> jogar(JOAO, id, 0), HttpStatus.FORBIDDEN, "participa");
		assertErro(() -> acao(JOAO, id, "resign"), HttpStatus.FORBIDDEN, "participa");
	}

	@Test
	void naoDesafiaASiMesmoNemDuasVezesParaOMesmoJogo() {
		assertErro(() -> criar(ALEXANDRE, alexandreId, null), HttpStatus.BAD_REQUEST, "si mesmo");
		assertErro(() -> criar(ALEXANDRE, 999_999L, null), HttpStatus.NOT_FOUND, "Usuario");

		criar(ALEXANDRE, mariaId, null);
		assertErro(() -> criar(MARIA, alexandreId, null), HttpStatus.CONFLICT, "aberto");

		// Com outra pessoa pode.
		assertThat(criar(ALEXANDRE, joaoId, null).status()).isEqualTo(GameStatus.INVITED);
	}

	@Test
	void desafioPelaConversaExigeOsDoisNela() {
		Long conversa = conversaDireta(ALEXANDRE, mariaId);

		GameResponse pelaConversa = criar(ALEXANDRE, mariaId, conversa);
		assertThat(pelaConversa.chatId()).isEqualTo(conversa);

		assertErro(() -> criar(ALEXANDRE, joaoId, conversa), HttpStatus.BAD_REQUEST, "conversa");
		assertErro(() -> criar(JOAO, mariaId, conversa), HttpStatus.FORBIDDEN, "conversa");
	}

	@Test
	void validaOCorpoDoDesafio() {
		assertErro(() -> rest.exchange(url("/api/v1/games"), HttpMethod.POST,
				json(ALEXANDRE, Map.of("opponentId", mariaId)), GameResponse.class), HttpStatus.BAD_REQUEST, "invalidos");
		assertErro(() -> rest.exchange(url("/api/v1/games"), HttpMethod.POST,
				json(ALEXANDRE, Map.of("type", "BATALHA_NAVAL", "opponentId", mariaId)), GameResponse.class),
				HttpStatus.BAD_REQUEST, null);
	}

	@Test
	void listaAsPartidasDoJogadorComAsEmAndamentoPrimeiro() {
		Long terminada = partidaEmAndamento();
		acao(ALEXANDRE, terminada, "resign");
		Long aberta = criar(JOAO, alexandreId, null).id();
		criar(MARIA, joaoId, null);

		List<GameResponse> doAlexandre = rest.exchange(url("/api/v1/games"), HttpMethod.GET, json(ALEXANDRE, null),
				new ParameterizedTypeReference<List<GameResponse>>() { }).getBody();

		assertThat(doAlexandre).extracting(GameResponse::id).containsExactly(aberta, terminada);
	}

	@Test
	void xadrezMateDoLoucoPelaApi() {
		Long id = criarDoTipo(GameType.CHESS);
		acao(MARIA, id, "accept");

		mover(ALEXANDRE, id, Map.of("move", "f2f3"));
		mover(MARIA, id, Map.of("move", "e7e5"));
		assertErro(() -> mover(ALEXANDRE, id, Map.of("move", "e1e3")), HttpStatus.BAD_REQUEST, "ilegal");
		mover(ALEXANDRE, id, Map.of("move", "g2g4"));
		GameResponse fim = mover(MARIA, id, Map.of("move", "d8h4"));

		assertThat(fim.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(fim.result()).isEqualTo(GameResult.WIN);
		assertThat(fim.winnerId()).isEqualTo(mariaId);
		assertThat(fim.state().get("ending").asText()).isEqualTo("CHECKMATE");
		assertThat(fim.state().get("san").get(3).asText()).isEqualTo("Qh4#");
	}

	@Test
	void damasComecaComAsBrancasDeQuemDesafiou() {
		Long id = criarDoTipo(GameType.CHECKERS);
		GameResponse aceita = acao(MARIA, id, "accept");

		assertThat(aceita.turnPlayerId()).isEqualTo(alexandreId);
		assertThat(aceita.state().get("moves")).hasSize(7);

		GameResponse depois = mover(ALEXANDRE, id, Map.of("path", List.of(42, 35)));
		assertThat(depois.turnPlayerId()).isEqualTo(mariaId);
		assertThat(depois.state().get("board").asText().charAt(35)).isEqualTo('w');
		assertErro(() -> mover(MARIA, id, Map.of("path", List.of(17, 33))), HttpStatus.BAD_REQUEST, "invalida");
	}

	@Test
	void jogosDiferentesEntreAMesmaDuplaPodemCorrerJuntos() {
		criarDoTipo(GameType.TIC_TAC_TOE);
		criarDoTipo(GameType.CHESS);

		assertThat(criarDoTipo(GameType.CHECKERS)).isNotNull();
	}

	@Test
	void ritmoEObrigatorioNosJogosComRelogioESoNeles() {
		assertErro(() -> criarComRitmo(GameType.CHESS, null), HttpStatus.BAD_REQUEST, "ritmo");
		assertErro(() -> criarComRitmo(GameType.CHECKERS, 7), HttpStatus.BAD_REQUEST, "ritmo");
		assertErro(() -> criarComRitmo(GameType.TIC_TAC_TOE, 5), HttpStatus.BAD_REQUEST, "relogio");

		GameResponse xadrez = criarComRitmo(GameType.CHESS, 3);
		assertThat(xadrez.clock().initialMs()).isEqualTo(180_000);
		assertThat(xadrez.clock().runningPlayerId()).isNull();
		assertThat(criar(ALEXANDRE, mariaId, null).clock()).isNull();
	}

	@Test
	void relogioComecaNoAceiteEPassaACadaJogada() {
		Long id = criarComRitmo(GameType.CHESS, 5).id();

		GameResponse aceita = acao(MARIA, id, "accept");
		assertThat(aceita.clock().runningPlayerId()).isEqualTo(alexandreId);
		assertThat(aceita.clock().opponentMs()).isEqualTo(300_000);
		assertThat(aceita.clock().challengerMs()).isBetween(295_000L, 300_000L);

		GameResponse depois = mover(ALEXANDRE, id, Map.of("move", "e2e4"));
		assertThat(depois.clock().runningPlayerId()).isEqualTo(mariaId);
		assertThat(depois.clock().challengerMs()).isBetween(295_000L, 300_000L);
	}

	@Test
	void tempoEsgotadoSemJogadaDaAVitoriaAoOutro() {
		Long id = criarComRitmo(GameType.CHESS, 3).id();
		acao(MARIA, id, "accept");
		mover(ALEXANDRE, id, Map.of("move", "e2e4"));

		// Daqui a 4 minutos o tempo da Maria (3 min) ja acabou.
		Instant futuro = Instant.now().plusSeconds(240);
		assertThat(gameService.findOutOfTime(futuro)).containsExactly(id);
		gameService.expireIfOutOfTime(id, futuro);

		GameResponse fim = rest.exchange(url("/api/v1/games/" + id), HttpMethod.GET, json(ALEXANDRE, null), GameResponse.class).getBody();
		assertThat(fim.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(fim.result()).isEqualTo(GameResult.TIMEOUT);
		assertThat(fim.winnerId()).isEqualTo(alexandreId);
		assertThat(fim.clock().opponentMs()).isZero();
		assertThat(fim.clock().runningPlayerId()).isNull();
		assertThat(gameService.findOutOfTime(futuro)).isEmpty();
	}

	@Test
	void verificacaoDeTempoNaoMexeEmQuemAindaTemTempo() {
		Long id = criarComRitmo(GameType.CHESS, 3).id();
		acao(MARIA, id, "accept");

		gameService.expireIfOutOfTime(id, Instant.now().plusSeconds(60));

		assertThat(rest.exchange(url("/api/v1/games/" + id), HttpMethod.GET, json(ALEXANDRE, null), GameResponse.class)
				.getBody().status()).isEqualTo(GameStatus.IN_PROGRESS);
	}

	@Test
	void empateCombinadoQuandoOAdversarioAceita() {
		Long id = criarDoTipo(GameType.CHECKERS);
		acao(MARIA, id, "accept");

		GameResponse proposta = acao(ALEXANDRE, id, "draw");
		assertThat(proposta.drawOfferedBy()).isEqualTo(alexandreId);
		assertErro(() -> acao(ALEXANDRE, id, "draw/accept"), HttpStatus.BAD_REQUEST, "proposta");

		GameResponse fim = acao(MARIA, id, "draw/accept");
		assertThat(fim.status()).isEqualTo(GameStatus.FINISHED);
		assertThat(fim.result()).isEqualTo(GameResult.AGREED_DRAW);
		assertThat(fim.winnerId()).isNull();
		assertThat(fim.drawOfferedBy()).isNull();
	}

	@Test
	void propostaRecusadaOuIgnoradaComUmaJogadaSome() {
		Long id = criarDoTipo(GameType.CHESS);
		acao(MARIA, id, "accept");

		acao(ALEXANDRE, id, "draw");
		GameResponse recusada = acao(MARIA, id, "draw/decline");
		assertThat(recusada.drawOfferedBy()).isNull();
		assertThat(recusada.status()).isEqualTo(GameStatus.IN_PROGRESS);
		assertErro(() -> acao(MARIA, id, "draw/decline"), HttpStatus.BAD_REQUEST, "proposta");

		acao(MARIA, id, "draw");
		GameResponse depoisDaJogada = mover(ALEXANDRE, id, Map.of("move", "e2e4"));
		assertThat(depoisDaJogada.drawOfferedBy()).isNull();
	}

	@Test
	void propostasCruzadasEmpatam() {
		Long id = criarDoTipo(GameType.CHESS);
		acao(MARIA, id, "accept");

		acao(MARIA, id, "draw");
		GameResponse fim = acao(ALEXANDRE, id, "draw");

		assertThat(fim.result()).isEqualTo(GameResult.AGREED_DRAW);
	}

	@Test
	void jogoDaVelhaNaoAceitaPropostaDeEmpate() {
		Long id = criar(ALEXANDRE, mariaId, null).id();
		acao(MARIA, id, "accept");

		assertErro(() -> acao(ALEXANDRE, id, "draw"), HttpStatus.BAD_REQUEST, "empate");
	}

	@Test
	void exigeAutenticacao() {
		assertThatThrownBy(() -> rest.getForEntity(url("/api/v1/games"), String.class))
				.isInstanceOfSatisfying(HttpClientErrorException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	private Long partidaEmAndamento() {
		Long id = criar(ALEXANDRE, mariaId, null).id();
		acao(MARIA, id, "accept");
		return id;
	}

	private GameResponse criar(String email, Long opponentId, Long chatId) {
		return rest.exchange(url("/api/v1/games"), HttpMethod.POST, json(email, desafio(opponentId, chatId)),
				GameResponse.class).getBody();
	}

	private Long criarDoTipo(GameType tipo) {
		return criarComRitmo(tipo, tipo.isTimed() ? 10 : null).id();
	}

	private GameResponse criarComRitmo(GameType tipo, Integer minutos) {
		Map<String, Object> body = desafio(mariaId, null);
		body.put("type", tipo);
		body.put("timeControlMinutes", minutos);
		return rest.exchange(url("/api/v1/games"), HttpMethod.POST, json(ALEXANDRE, body), GameResponse.class)
				.getBody();
	}

	private GameResponse mover(String email, Long gameId, Map<String, Object> jogada) {
		return rest.exchange(url("/api/v1/games/" + gameId + "/moves"), HttpMethod.POST, json(email, jogada),
				GameResponse.class).getBody();
	}

	private GameResponse acao(String email, Long gameId, String acao) {
		return rest.exchange(url("/api/v1/games/" + gameId + "/" + acao), HttpMethod.POST, json(email, null),
				GameResponse.class).getBody();
	}

	private GameResponse jogar(String email, Long gameId, int cell) {
		return rest.exchange(url("/api/v1/games/" + gameId + "/moves"), HttpMethod.POST,
				json(email, Map.of("cell", cell)), GameResponse.class).getBody();
	}

	private Long conversaDireta(String email, Long participantId) {
		Map<?, ?> chat = rest.exchange(url("/api/v1/chats"), HttpMethod.POST,
				json(email, Map.of("participantId", participantId)), Map.class).getBody();
		return ((Number) chat.get("id")).longValue();
	}

	private static Map<String, Object> desafio(Long opponentId, Long chatId) {
		Map<String, Object> body = new HashMap<>();
		body.put("type", GameType.TIC_TAC_TOE);
		body.put("opponentId", opponentId);
		body.put("chatId", chatId);
		return body;
	}

	/** Le os eventos que chegarem e devolve o ultimo depois de {@code esperados}. */
	private static GameResponse ultimo(BlockingQueue<GameResponse> fila, int esperados) throws InterruptedException {
		GameResponse ultimo = null;
		for (int i = 0; i < esperados; i++) {
			GameResponse evento = fila.poll(5, TimeUnit.SECONDS);
			assertThat(evento).as("evento %d de %d", i + 1, esperados).isNotNull();
			ultimo = evento;
		}
		return ultimo;
	}

	private static void assertErro(Runnable chamada, HttpStatus status, String trecho) {
		assertThatThrownBy(chamada::run)
				.isInstanceOfSatisfying(HttpClientErrorException.class, ex -> {
					assertThat(ex.getStatusCode()).isEqualTo(status);
					if (trecho != null) {
						assertThat(ex.getResponseBodyAsString()).containsIgnoringCase(trecho);
					}
				});
	}

}
