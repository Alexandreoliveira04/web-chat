package br.edu.webchat.game.engine.checkers;

import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckersEngineTest {

	private final CheckersEngine engine = new CheckersEngine();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void comecaComDozePecasDeCadaLadoEBrancasJogando() {
		CheckersState state = engine.initialState();

		assertThat(state.board().chars().filter(c -> c == 'w').count()).isEqualTo(12);
		assertThat(state.board().chars().filter(c -> c == 'b').count()).isEqualTo(12);
		assertThat(state.toMove()).isZero();
		// Fila da frente das brancas (linha 5): casas 40, 42, 44 e 46.
		assertThat(state.moves()).containsExactlyInAnyOrder(
				List.of(40, 33), List.of(42, 33), List.of(42, 35), List.of(44, 35), List.of(44, 37),
				List.of(46, 37), List.of(46, 39));
		assertThat(engine.outcome(state)).isEqualTo(GameOutcome.ONGOING);
	}

	@Test
	void pedraAndaParaAFrenteEPassaAVez() {
		CheckersState depois = engine.applyMove(engine.initialState(), 0, path(42, 35));

		assertThat(depois.board().charAt(42)).isEqualTo('.');
		assertThat(depois.board().charAt(35)).isEqualTo('w');
		assertThat(depois.toMove()).isEqualTo(1);
		assertThat(depois.lastMove()).containsExactly(42, 35);
		assertThat(depois.moves()).allSatisfy(move -> assertThat(move.get(1) / 8).isGreaterThan(move.get(0) / 8));
	}

	@Test
	void capturaEObrigatoria() {
		// Branca em 44 (linha 5) pode capturar a preta em 35 caindo em 26; a branca em 46 poderia so andar.
		CheckersState state = estado(Map.of(44, 'w', 46, 'w', 35, 'b', 1, 'b'), 0);

		assertThat(state.moves()).containsExactly(List.of(44, 26));
		assertThatThrownBy(() -> engine.applyMove(state, 0, path(46, 37)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("obrigatoria");

		CheckersState depois = engine.applyMove(state, 0, path(44, 26));
		assertThat(depois.board().charAt(35)).isEqualTo('.');
		assertThat(depois.board().charAt(26)).isEqualTo('w');
		assertThat(depois.lastCaptured()).containsExactly(35);
	}

	@Test
	void pedraCapturaParaTras() {
		// Branca em 26 (linha 3), preta atras dela em 35, casa 44 livre.
		CheckersState state = estado(Map.of(26, 'w', 35, 'b', 1, 'b'), 0);

		assertThat(state.moves()).containsExactly(List.of(26, 44));
	}

	@Test
	void leiDaMaioriaObrigaASequenciaQueCapturaMais() {
		// Da casa 44: para a esquerda captura so 35; para a direita captura 37 e depois 21.
		CheckersState state = estado(Map.of(44, 'w', 35, 'b', 37, 'b', 21, 'b', 0, 'b'), 0);

		assertThat(state.moves()).containsExactly(List.of(44, 30, 12));
		assertThatThrownBy(() -> engine.applyMove(state, 0, path(44, 26))).isInstanceOf(BadRequestException.class);

		CheckersState depois = engine.applyMove(state, 0, path(44, 30, 12));
		assertThat(depois.board().charAt(37)).isEqualTo('.');
		assertThat(depois.board().charAt(21)).isEqualTo('.');
		assertThat(depois.board().charAt(35)).isEqualTo('b');
		assertThat(depois.lastCaptured()).containsExactly(37, 21);
	}

	@Test
	void pedraQueTerminaNaUltimaLinhaViraDama() {
		// Branca em 10 (linha 1) anda para 1, na linha 0.
		CheckersState state = estado(Map.of(10, 'w', 63, 'b'), 0);

		CheckersState depois = engine.applyMove(state, 0, path(10, 1));

		assertThat(depois.board().charAt(1)).isEqualTo('W');
	}

	@Test
	void passarPelaUltimaLinhaNoMeioDaCapturaNaoPromove() {
		// 17 captura 10 e cai em 3 (linha 0), mas e obrigada a seguir capturando 12 ate 21.
		CheckersState state = estado(Map.of(17, 'w', 10, 'b', 12, 'b', 63, 'b'), 0);

		assertThat(state.moves()).containsExactly(List.of(17, 3, 21));

		CheckersState depois = engine.applyMove(state, 0, path(17, 3, 21));
		assertThat(depois.board().charAt(21)).isEqualTo('w');
	}

	@Test
	void damaVoaAndandoECapturandoAQualquerDistancia() {
		// Dama branca em 56 (canto inferior esquerdo), diagonal livre ate 7.
		CheckersState livre = estado(Map.of(56, 'W', 0, 'b'), 0);
		assertThat(livre.moves()).contains(List.of(56, 49), List.of(56, 14), List.of(56, 7));

		// Preta em 28 na diagonal: a dama pode pousar em qualquer casa livre depois dela.
		CheckersState captura = estado(Map.of(56, 'W', 28, 'b', 0, 'b'), 0);
		assertThat(captura.moves()).containsExactlyInAnyOrder(List.of(56, 21), List.of(56, 14), List.of(56, 7));

		CheckersState depois = engine.applyMove(captura, 0, path(56, 7));
		assertThat(depois.board().charAt(28)).isEqualTo('.');
		assertThat(depois.board().charAt(7)).isEqualTo('W');
	}

	@Test
	void damaNaoPulaDuasPecasSeguidas() {
		CheckersState state = estado(Map.of(56, 'W', 42, 'b', 35, 'b', 0, 'b'), 0);

		assertThat(state.moves()).allSatisfy(move -> assertThat(move).hasSize(2));
		assertThat(state.moves()).doesNotContain(List.of(56, 28));
	}

	@Test
	void quemFicaSemPecasOuSemJogadaPerde() {
		CheckersState state = estado(Map.of(44, 'w', 35, 'b'), 0);

		CheckersState depois = engine.applyMove(state, 0, path(44, 26));

		assertThat(depois.moves()).isEmpty();
		assertThat(engine.outcome(depois)).isEqualTo(GameOutcome.win(0));
		assertThatThrownBy(() -> engine.applyMove(depois, 1, path(1, 8))).hasMessageContaining("terminou");
	}

	@Test
	void empataDepoisDeJogadasSeguidasSoComDamasSemCaptura() {
		CheckersState quase = comContador(estado(Map.of(56, 'W', 7, 'B'), 0), CheckersEngine.DRAW_AFTER_QUIET_MOVES - 1);

		CheckersState depois = engine.applyMove(quase, 0, path(56, 49));

		assertThat(depois.quietMoves()).isEqualTo(CheckersEngine.DRAW_AFTER_QUIET_MOVES);
		assertThat(engine.outcome(depois)).isEqualTo(GameOutcome.DRAW);
	}

	@Test
	void movimentoDePedraZeraOContador() {
		CheckersState state = comContador(estado(Map.of(44, 'w', 7, 'B'), 0), 10);

		assertThat(engine.applyMove(state, 0, path(44, 35)).quietMoves()).isZero();
	}

	@Test
	void recusaJogadaForaDaVezOuMalFormada() {
		CheckersState inicial = engine.initialState();

		assertThatThrownBy(() -> engine.applyMove(inicial, 1, path(17, 24))).hasMessageContaining("vez");
		assertThatThrownBy(() -> engine.applyMove(inicial, 0, path(42))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(inicial, 0, path(42, 99))).isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> engine.applyMove(inicial, 0, path(42, 34))).hasMessageContaining("invalida");
		assertThatThrownBy(() -> engine.applyMove(inicial, 0, null)).isInstanceOf(BadRequestException.class);
	}

	@Test
	void estadoSobreviveAIdaEVoltaPeloJson() throws Exception {
		CheckersState state = engine.applyMove(engine.initialState(), 0, path(42, 35));

		String json = objectMapper.writeValueAsString(state);

		assertThat(objectMapper.readValue(json, CheckersState.class)).isEqualTo(state);
	}

	/** Estado com as pecas dadas e as jogadas legais ja calculadas, como o motor faria. */
	private CheckersState estado(Map<Integer, Character> pecas, int toMove) {
		char[] board = ".".repeat(64).toCharArray();
		pecas.forEach((casa, peca) -> board[casa] = peca);
		String texto = new String(board);
		List<List<Integer>> moves = engine.legalMoves(texto, toMove).stream().map(CheckersEngine.Move::path).toList();
		return new CheckersState(texto, toMove, 0, moves, List.of(), List.of());
	}

	private static CheckersState comContador(CheckersState state, int quietMoves) {
		return new CheckersState(state.board(), state.toMove(), quietMoves, state.moves(), state.lastMove(), state.lastCaptured());
	}

	private JsonNode path(int... casas) {
		ObjectNode move = objectMapper.createObjectNode();
		ArrayNode path = move.putArray("path");
		for (int casa : casas) {
			path.add(casa);
		}
		return move;
	}

}
