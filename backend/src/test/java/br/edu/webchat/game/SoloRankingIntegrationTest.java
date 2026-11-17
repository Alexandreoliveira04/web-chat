package br.edu.webchat.game;

import br.edu.webchat.game.dto.SoloRankingResponse;
import br.edu.webchat.game.dto.SoloResultResponse;
import br.edu.webchat.support.StompIntegrationTest;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ranking dos jogos solo com servidor, seguranca e banco reais. */
class SoloRankingIntegrationTest extends StompIntegrationTest {

	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String MARIA = "maria@email.com";
	private static final String JOAO = "joao@email.com";

	@Autowired
	private UserService userService;

	private Long alexandreId;
	private Long mariaId;

	@BeforeEach
	void setUp() {
		limparBase();
		alexandreId = userService.create(new CreateUserRequest("Alexandre", ALEXANDRE, "123456")).id();
		mariaId = userService.create(new CreateUserRequest("Maria", MARIA, "123456")).id();
		userService.create(new CreateUserRequest("Joao", JOAO, "123456"));
	}

	@Test
	void primeiraVitoriaEhRecordeEAparecesNoRanking() {
		ResponseEntity<SoloResultResponse> resposta = registrar(ALEXANDRE, "MINESWEEPER", "EASY", 42_000L, null);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		SoloResultResponse corpo = resposta.getBody();
		assertThat(corpo.personalBest()).isTrue();
		assertThat(corpo.ranking().me().position()).isEqualTo(1);
		assertThat(corpo.ranking().me().bestDurationMs()).isEqualTo(42_000L);
		assertThat(corpo.ranking().top()).hasSize(1);
	}

	@Test
	void rankingUsaOMelhorTempoDeCadaPessoaEContaAsVitorias() {
		registrar(ALEXANDRE, "MINESWEEPER", "EASY", 50_000L, null);
		SoloResultResponse pior = registrar(ALEXANDRE, "MINESWEEPER", "EASY", 60_000L, null).getBody();
		assertThat(pior.personalBest()).isFalse();
		SoloResultResponse melhor = registrar(ALEXANDRE, "MINESWEEPER", "EASY", 30_000L, null).getBody();
		assertThat(melhor.personalBest()).isTrue();
		registrar(MARIA, "MINESWEEPER", "EASY", 40_000L, null);

		SoloRankingResponse ranking = ranking(ALEXANDRE, "MINESWEEPER", "EASY");

		assertThat(ranking.top()).extracting(entrada -> entrada.player().id()).containsExactly(alexandreId, mariaId);
		assertThat(ranking.top().get(0).bestDurationMs()).isEqualTo(30_000L);
		assertThat(ranking.top().get(0).wins()).isEqualTo(3);
		assertThat(ranking.top().get(1).position()).isEqualTo(2);
		assertThat(ranking.me().position()).isEqualTo(1);
	}

	@Test
	void rankingESeparadoPorJogoEModo() {
		registrar(ALEXANDRE, "MINESWEEPER", "EASY", 30_000L, null);
		registrar(MARIA, "MINESWEEPER", "HARD", 300_000L, null);
		registrar(MARIA, "SOLITAIRE", "DRAW_ONE", 200_000L, 120);

		assertThat(ranking(MARIA, "MINESWEEPER", "EASY").me()).isNull();
		assertThat(ranking(MARIA, "MINESWEEPER", "HARD").top()).hasSize(1);
		assertThat(ranking(ALEXANDRE, "SOLITAIRE", "DRAW_ONE").top())
				.extracting(entrada -> entrada.player().id()).containsExactly(mariaId);
		assertThat(ranking(ALEXANDRE, "SOLITAIRE", "DRAW_THREE").top()).isEmpty();
	}

	@Test
	void quemNaoVenceuNaoTemPosicaoMasVeORanking() {
		registrar(MARIA, "SOLITAIRE", "DRAW_THREE", 100_000L, 90);

		SoloRankingResponse ranking = ranking(JOAO, "SOLITAIRE", "DRAW_THREE");

		assertThat(ranking.me()).isNull();
		assertThat(ranking.top()).hasSize(1);
	}

	@Test
	void recusaJogoModoOuTempoInvalidos() {
		assertErro(() -> registrar(ALEXANDRE, "MINESWEEPER", "IMPOSSIVEL", 30_000L, null), HttpStatus.BAD_REQUEST, "Modo invalido");
		assertErro(() -> registrar(ALEXANDRE, "SOLITAIRE", "EASY", 30_000L, null), HttpStatus.BAD_REQUEST, "Modo invalido");
		assertErro(() -> registrar(ALEXANDRE, "BATALHA_NAVAL", "EASY", 30_000L, null), HttpStatus.BAD_REQUEST, null);
		assertErro(() -> registrar(ALEXANDRE, "MINESWEEPER", "EASY", 500L, null), HttpStatus.BAD_REQUEST, "1 segundo");
		assertErro(() -> registrar(ALEXANDRE, "MINESWEEPER", "EASY", 100_000_000L, null), HttpStatus.BAD_REQUEST, "24 horas");
		assertErro(() -> registrar(ALEXANDRE, "SOLITAIRE", "DRAW_ONE", 30_000L, -1), HttpStatus.BAD_REQUEST, "negativo");
		assertErro(() -> ranking(ALEXANDRE, "MINESWEEPER", "QUALQUER"), HttpStatus.BAD_REQUEST, "Modo invalido");
	}

	@Test
	void aceitaOModoEmMinusculas() {
		assertThat(registrar(ALEXANDRE, "MINESWEEPER", "medium", 90_000L, null).getBody().ranking().mode()).isEqualTo("MEDIUM");
	}

	@Test
	void exigeAutenticacao() {
		assertThatThrownBy(() -> rest.getForEntity(url("/api/v1/games/solo/MINESWEEPER/ranking?mode=EASY"), String.class))
				.isInstanceOfSatisfying(HttpClientErrorException.class,
						ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	private ResponseEntity<SoloResultResponse> registrar(String email, String jogo, String modo, Long tempo, Integer jogadas) {
		Map<String, Object> body = new HashMap<>();
		body.put("mode", modo);
		body.put("durationMs", tempo);
		body.put("moves", jogadas);
		return rest.exchange(url("/api/v1/games/solo/" + jogo + "/results"), HttpMethod.POST, json(email, body),
				SoloResultResponse.class);
	}

	private SoloRankingResponse ranking(String email, String jogo, String modo) {
		return rest.exchange(url("/api/v1/games/solo/" + jogo + "/ranking?mode=" + modo), HttpMethod.GET,
				json(email, null), SoloRankingResponse.class).getBody();
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
