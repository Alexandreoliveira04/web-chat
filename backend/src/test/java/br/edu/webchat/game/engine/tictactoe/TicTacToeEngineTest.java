package br.edu.webchat.game.engine.tictactoe;

import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicTacToeEngineTest {

	private final TicTacToeEngine engine = new TicTacToeEngine();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void comecaVazioComQuemDesafiouJogandoDeX() {
		TicTacToeState state = engine.initialState();

		assertThat(state.board()).isEqualTo(".........");
		assertThat(engine.playerToMove(state)).isZero();
		assertThat(engine.outcome(state)).isEqualTo(GameOutcome.ONGOING);
	}

	@Test
	void jogadaMarcaACasaEPassaAVez() {
		TicTacToeState depois = engine.applyMove(engine.initialState(), 0, cell(4));

		assertThat(depois.board()).isEqualTo("....X....");
		assertThat(engine.playerToMove(depois)).isEqualTo(1);

		TicTacToeState resposta = engine.applyMove(depois, 1, cell(0));
		assertThat(resposta.board()).isEqualTo("O...X....");
		assertThat(engine.playerToMove(resposta)).isZero();
	}

	@Test
	void naoAlteraOEstadoRecebido() {
		TicTacToeState inicial = engine.initialState();

		engine.applyMove(inicial, 0, cell(4));

		assertThat(inicial.board()).isEqualTo(".........");
	}

	@Test
	void recusaJogadaForaDaVez() {
		assertThatThrownBy(() -> engine.applyMove(engine.initialState(), 1, cell(4)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("vez");
	}

	@Test
	void recusaCasaOcupada() {
		TicTacToeState state = new TicTacToeState("....X....", 1);

		assertThatThrownBy(() -> engine.applyMove(state, 1, cell(4)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("ocupada");
	}

	@Test
	void recusaCasaInexistenteOuJogadaMalFormada() {
		TicTacToeState state = engine.initialState();

		assertThatThrownBy(() -> engine.applyMove(state, 0, cell(9))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(state, 0, cell(-1))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(state, 0, json("{\"cell\": \"4\"}"))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(state, 0, json("{}"))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(state, 0, null)).isInstanceOf(BadRequestException.class);
	}

	@Test
	void reconheceVitoriaEmLinhaColunaEDiagonal() {
		assertThat(engine.outcome(new TicTacToeState("XXXOO....", 1))).isEqualTo(GameOutcome.win(0));
		assertThat(engine.outcome(new TicTacToeState("OXXOX.O..", 0))).isEqualTo(GameOutcome.win(1));
		assertThat(engine.outcome(new TicTacToeState("XO.OX...X", 1))).isEqualTo(GameOutcome.win(0));
		assertThat(engine.outcome(new TicTacToeState("XXO.O.OX.", 0))).isEqualTo(GameOutcome.win(1));
	}

	@Test
	void tabuleiroCheioSemVencedorEEmpate() {
		assertThat(engine.outcome(new TicTacToeState("XOXXOOOXX", 1))).isEqualTo(GameOutcome.DRAW);
	}

	@Test
	void naoAceitaJogadaDepoisDoFim() {
		TicTacToeState vencida = new TicTacToeState("XXXOO....", 1);

		assertThatThrownBy(() -> engine.applyMove(vencida, 1, cell(8)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("terminou");
	}

	@Test
	void estadoSobreviveAIdaEVoltaPeloJson() throws Exception {
		TicTacToeState state = new TicTacToeState("X...O....", 0);

		String json = objectMapper.writeValueAsString(state);

		assertThat(objectMapper.readValue(json, TicTacToeState.class)).isEqualTo(state);
	}

	private JsonNode cell(int cell) {
		return objectMapper.createObjectNode().put("cell", cell);
	}

	private JsonNode json(String json) {
		try {
			return objectMapper.readTree(json);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException(ex);
		}
	}

}
