package br.edu.webchat.game.engine.chess;

import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChessEngineTest {

	private final ChessEngine engine = new ChessEngine();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void comecaNaPosicaoInicialComBrancasJogando() {
		ChessState state = engine.initialState();

		assertThat(state.fen()).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		assertThat(state.toMove()).isZero();
		assertThat(state.moves()).hasSize(20).contains("e2e4", "g1f3");
		assertThat(state.history()).isEmpty();
		assertThat(state.check()).isFalse();
		assertThat(engine.outcome(state)).isEqualTo(GameOutcome.ONGOING);
	}

	@Test
	void jogadaAtualizaPosicaoHistoricoEVez() {
		ChessState depois = jogar(engine.initialState(), "e2e4");

		assertThat(depois.toMove()).isEqualTo(1);
		assertThat(depois.history()).containsExactly("e2e4");
		assertThat(depois.san()).containsExactly("e4");
		assertThat(depois.fen()).startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b");
	}

	@Test
	void recusaJogadaIlegalForaDaVezOuMalFormada() {
		ChessState inicial = engine.initialState();

		// O doMove da biblioteca aceitaria esta; o motor confere contra as legais.
		assertThatThrownBy(() -> jogar(inicial, "e2e5")).isInstanceOf(BadRequestException.class).hasMessageContaining("ilegal");
		assertThatThrownBy(() -> engine.applyMove(inicial, 1, move("e7e5"))).hasMessageContaining("vez");
		assertThatThrownBy(() -> engine.applyMove(inicial, 0, objectMapper.createObjectNode())).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(inicial, 0, null)).isInstanceOf(BadRequestException.class);
	}

	@Test
	void mateDoLoucoDaVitoriaAsPretas() {
		ChessState state = jogar(engine.initialState(), "f2f3", "e7e5", "g2g4", "d8h4");

		assertThat(state.ending()).isEqualTo(ChessEnding.CHECKMATE);
		assertThat(state.check()).isTrue();
		assertThat(state.moves()).isEmpty();
		assertThat(state.san()).last().isEqualTo("Qh4#");
		assertThat(engine.outcome(state)).isEqualTo(GameOutcome.win(1));
		assertThatThrownBy(() -> jogar(state, "a2a3")).hasMessageContaining("terminou");
	}

	@Test
	void promocaoIndicaAPecaNoFimDaJogada() {
		ChessState antes = jogar(engine.initialState(),
				"e2e4", "d7d5", "e4d5", "c7c6", "d5c6", "d8b6", "c6b7", "b6b2");

		assertThat(antes.moves()).contains("b7a8q", "b7a8n", "b7c8q");
		assertThatThrownBy(() -> jogar(antes, "b7a8")).isInstanceOf(BadRequestException.class);

		ChessState depois = jogar(antes, "b7a8n");
		assertThat(depois.san()).last().isEqualTo("bxa8=N");
		assertThat(depois.fen()).startsWith("Nn");
	}

	@Test
	void repeticaoTriplaEmpata() {
		ChessState state = jogar(engine.initialState(),
				"g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");

		assertThat(state.ending()).isEqualTo(ChessEnding.REPETITION);
		assertThat(engine.outcome(state)).isEqualTo(GameOutcome.DRAW);
	}

	@Test
	void estadoSobreviveAIdaEVoltaPeloJson() throws Exception {
		ChessState state = jogar(engine.initialState(), "e2e4", "e7e5");

		String json = objectMapper.writeValueAsString(state);

		assertThat(objectMapper.readValue(json, ChessState.class)).isEqualTo(state);
	}

	private ChessState jogar(ChessState state, String... jogadas) {
		ChessState atual = state;
		for (String jogada : jogadas) {
			atual = engine.applyMove(atual, atual.toMove(), move(jogada));
		}
		return atual;
	}

	private JsonNode move(String uci) {
		return objectMapper.createObjectNode().put("move", uci);
	}

}
