package br.edu.webchat.game;

import br.edu.webchat.game.dto.GameResponse;
import br.edu.webchat.game.entity.GameResult;
import br.edu.webchat.game.entity.GameStatus;
import br.edu.webchat.game.entity.GameType;
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
