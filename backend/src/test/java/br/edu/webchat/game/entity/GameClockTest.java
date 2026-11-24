package br.edu.webchat.game.entity;

import br.edu.webchat.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Relogio da partida: o tempo de quem joga corre durante a vez e e descontado ao fim dela. */
class GameClockTest {

	private static final Instant INICIO = Instant.parse("2026-11-16T20:00:00Z");

	private Game game;

	@BeforeEach
	void setUp() {
		game = new Game(GameType.CHESS, usuario(1L), usuario(2L), null, "{}");
		game.useTimeControl(Duration.ofMinutes(3));
	}

	@Test
	void jogoSemRitmoNaoTemRelogio() {
		Game semRelogio = new Game(GameType.TIC_TAC_TOE, usuario(1L), usuario(2L), null, "{}");

		semRelogio.startTurn(0, INICIO);

		assertThat(semRelogio.isTimed()).isFalse();
		assertThat(semRelogio.endTurn(0, INICIO.plusSeconds(3600))).isTrue();
		assertThat(semRelogio.isOutOfTime(INICIO.plusSeconds(3600))).isFalse();
	}

	@Test
	void soOTempoDeQuemJogaCorre() {
		game.startTurn(Game.CHALLENGER, INICIO);

		Instant depois = INICIO.plusSeconds(40);
		assertThat(game.timeLeft(Game.CHALLENGER, Game.CHALLENGER, depois)).isEqualTo(140_000);
		assertThat(game.timeLeft(Game.OPPONENT, Game.CHALLENGER, depois)).isEqualTo(180_000);
		assertThat(game.getTurnDeadlineAt()).isEqualTo(INICIO.plusSeconds(180));
	}

	@Test
	void fimDaVezDescontaOTempoGastoEPassaORelogio() {
		game.startTurn(Game.CHALLENGER, INICIO);

		assertThat(game.endTurn(Game.CHALLENGER, INICIO.plusSeconds(30))).isTrue();
		game.startTurn(Game.OPPONENT, INICIO.plusSeconds(30));

		Instant depois = INICIO.plusSeconds(50);
		assertThat(game.timeLeft(Game.CHALLENGER, Game.OPPONENT, depois)).isEqualTo(150_000);
		assertThat(game.timeLeft(Game.OPPONENT, Game.OPPONENT, depois)).isEqualTo(160_000);
		assertThat(game.getTurnDeadlineAt()).isEqualTo(INICIO.plusSeconds(30 + 180));
	}

	@Test
	void tempoEsgotadoNaVez() {
		game.startTurn(Game.CHALLENGER, INICIO);

		assertThat(game.isOutOfTime(INICIO.plusSeconds(179))).isFalse();
		assertThat(game.isOutOfTime(INICIO.plusSeconds(180))).isTrue();
		assertThat(game.endTurn(Game.CHALLENGER, INICIO.plusSeconds(181))).isFalse();
		assertThat(game.timeLeft(Game.CHALLENGER, -1, INICIO.plusSeconds(200))).isZero();
	}

	@Test
	void terminarAPartidaParaORelogioELimpaAPropostaDeEmpate() {
		game.startTurn(Game.CHALLENGER, INICIO);
		game.offerDraw(game.getOpponent());

		game.finish(GameResult.RESIGNATION, game.getOpponent());

		assertThat(game.getTurnStartedAt()).isNull();
		assertThat(game.getTurnDeadlineAt()).isNull();
		assertThat(game.getDrawOfferedById()).isNull();
		assertThat(game.isOutOfTime(INICIO.plusSeconds(3600))).isFalse();
	}

	private static User usuario(Long id) {
		User user = new User("Usuario " + id, "u" + id + "@email.com", "senha");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
