package br.edu.webchat.game.engine.checkers;

import br.edu.webchat.game.engine.GameEngine;
import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.game.entity.GameType;
import br.edu.webchat.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Damas pelas regras brasileiras:
 * <ul>
 *   <li>pedra anda uma casa na diagonal para a frente, mas captura para a frente e para tras;</li>
 *   <li>a captura e obrigatoria e, entre as possiveis, vale a <b>lei da maioria</b>: e preciso
 *       escolher uma sequencia que capture o maior numero de pecas;</li>
 *   <li>numa sequencia, as pecas capturadas so saem do tabuleiro no fim, nao podem ser puladas
 *       duas vezes e continuam bloqueando o caminho;</li>
 *   <li>a dama "voa": anda e captura a qualquer distancia na diagonal;</li>
 *   <li>pedra que termina a jogada na ultima linha vira dama (passar por ela no meio de uma
 *       captura nao conta);</li>
 *   <li>perde quem nao tem jogada (sem pecas ou bloqueado);</li>
 *   <li>empate depois de {@value #DRAW_AFTER_QUIET_MOVES} jogadas seguidas (20 de cada lado) so
 *       com damas e sem captura.</li>
 * </ul>
 *
 * <p>A jogada e {@code {"path": [origem, casa1, casa2, ...]}}: a origem e cada casa onde a
 * peca para. Numa captura multipla, cada parada vem no caminho.
 */
@Component
public class CheckersEngine implements GameEngine<CheckersState> {

	static final int SIZE = 8;
	static final int DRAW_AFTER_QUIET_MOVES = 40;

	private static final char EMPTY = '.';
	private static final char[] MEN = {'w', 'b'};
	private static final char[] KINGS = {'W', 'B'};
	/** Linha para onde cada jogador avanca: brancas (0) sobem, pretas (1) descem. */
	private static final int[] FORWARD = {-1, 1};
	private static final int[] PROMOTION_ROW = {0, SIZE - 1};
	private static final int[][] DIAGONALS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

	/** Jogada com as casas das pecas que ela captura, para aplicar no tabuleiro. */
	record Move(List<Integer> path, List<Integer> captured) {
	}

	@Override
	public GameType type() {
		return GameType.CHECKERS;
	}

	@Override
	public Class<CheckersState> stateType() {
		return CheckersState.class;
	}

	@Override
	public CheckersState initialState() {
		StringBuilder board = new StringBuilder();
		for (int row = 0; row < SIZE; row++) {
			for (int col = 0; col < SIZE; col++) {
				boolean dark = (row + col) % 2 == 1;
				if (dark && row < 3) {
					board.append(MEN[1]);
				}
				else if (dark && row > 4) {
					board.append(MEN[0]);
				}
				else {
					board.append(EMPTY);
				}
			}
		}
		return state(board.toString(), 0, 0, List.of(), List.of());
	}

	@Override
	public int playerToMove(CheckersState state) {
		return state.toMove();
	}

	@Override
	public CheckersState applyMove(CheckersState state, int player, JsonNode move) {
		if (outcome(state).finished()) {
			throw new BadRequestException("A partida ja terminou");
		}
		if (player != state.toMove()) {
			throw new BadRequestException("Nao e a sua vez");
		}

		List<Integer> path = parsePath(move);
		List<Move> legal = legalMoves(state.board(), player);
		Move chosen = legal.stream()
				.filter(candidate -> candidate.path().equals(path))
				.findFirst()
				.orElseThrow(() -> new BadRequestException(legal.stream().anyMatch(m -> !m.captured().isEmpty())
						? "A captura e obrigatoria, e pela lei da maioria vale a que captura mais pecas"
						: "Jogada invalida"));

		char[] board = state.board().toCharArray();
		int from = path.get(0);
		int to = path.get(path.size() - 1);
		char piece = board[from];
		boolean wasKing = piece == KINGS[player];

		board[from] = EMPTY;
		chosen.captured().forEach(square -> board[square] = EMPTY);
		board[to] = !wasKing && to / SIZE == PROMOTION_ROW[player] ? KINGS[player] : piece;

		int quietMoves = wasKing && chosen.captured().isEmpty() ? state.quietMoves() + 1 : 0;
		return state(new String(board), 1 - player, quietMoves, path, chosen.captured());
	}

	@Override
	public GameOutcome outcome(CheckersState state) {
		if (legalMoves(state.board(), state.toMove()).isEmpty()) {
			return GameOutcome.win(1 - state.toMove());
		}
		if (state.quietMoves() >= DRAW_AFTER_QUIET_MOVES) {
			return GameOutcome.DRAW;
		}
		return GameOutcome.ONGOING;
	}

	private CheckersState state(String board, int toMove, int quietMoves, List<Integer> lastMove, List<Integer> lastCaptured) {
		List<List<Integer>> moves = legalMoves(board, toMove).stream().map(Move::path).toList();
		return new CheckersState(board, toMove, quietMoves, moves, lastMove, lastCaptured);
	}

	/** Todas as jogadas legais: so capturas (as de maior numero de pecas) se houver alguma, senao os movimentos simples. */
	List<Move> legalMoves(String boardText, int player) {
		char[] board = boardText.toCharArray();

		List<Move> captures = new ArrayList<>();
		for (int square = 0; square < board.length; square++) {
			if (owns(board[square], player)) {
				collectCaptures(board, square, square, board[square] == KINGS[player], player,
						List.of(square), List.of(), captures);
			}
		}
		if (!captures.isEmpty()) {
			int most = captures.stream().mapToInt(m -> m.captured().size()).max().orElseThrow();
			return captures.stream().filter(m -> m.captured().size() == most).toList();
		}

		List<Move> moves = new ArrayList<>();
		for (int square = 0; square < board.length; square++) {
			if (board[square] == MEN[player]) {
				for (int dc : new int[] {-1, 1}) {
					int target = step(square, FORWARD[player], dc, 1);
					if (target >= 0 && board[target] == EMPTY) {
						moves.add(new Move(List.of(square, target), List.of()));
					}
				}
			}
			else if (board[square] == KINGS[player]) {
				for (int[] dir : DIAGONALS) {
					for (int n = 1; ; n++) {
						int target = step(square, dir[0], dir[1], n);
						if (target < 0 || board[target] != EMPTY) {
							break;
						}
						moves.add(new Move(List.of(square, target), List.of()));
					}
				}
			}
		}
		return moves;
	}

	/**
	 * Busca em profundidade das sequencias de captura a partir de {@code current}. A casa de
	 * origem conta como vazia (a peca saiu dela); as capturadas continuam no tabuleiro ate o fim.
	 */
	private void collectCaptures(char[] board, int origin, int current, boolean king, int player,
			List<Integer> path, List<Integer> captured, List<Move> out) {
		boolean continued = false;

		for (int[] dir : DIAGONALS) {
			if (king) {
				int victim = step(current, dir[0], dir[1], 1);
				while (victim >= 0 && isFree(board, victim, origin)) {
					victim = step(victim, dir[0], dir[1], 1);
				}
				if (victim < 0 || !isOpponent(board[victim], player) || captured.contains(victim)) {
					continue;
				}
				for (int landing = step(victim, dir[0], dir[1], 1);
						landing >= 0 && isFree(board, landing, origin);
						landing = step(landing, dir[0], dir[1], 1)) {
					collectCaptures(board, origin, landing, true, player, append(path, landing), append(captured, victim), out);
					continued = true;
				}
			}
			else {
				int victim = step(current, dir[0], dir[1], 1);
				int landing = step(current, dir[0], dir[1], 2);
				if (landing >= 0 && isOpponent(board[victim], player) && !captured.contains(victim)
						&& isFree(board, landing, origin)) {
					collectCaptures(board, origin, landing, false, player, append(path, landing), append(captured, victim), out);
					continued = true;
				}
			}
		}

		if (!continued && !captured.isEmpty()) {
			out.add(new Move(path, captured));
		}
	}

	private static List<Integer> parsePath(JsonNode move) {
		JsonNode node = move == null ? null : move.get("path");
		if (node == null || !node.isArray() || node.size() < 2) {
			throw new BadRequestException("Informe o caminho da jogada: a casa de origem e as casas de destino");
		}

		List<Integer> path = new ArrayList<>();
		for (JsonNode square : node) {
			if (!square.isInt() || square.asInt() < 0 || square.asInt() >= SIZE * SIZE) {
				throw new BadRequestException("Casa invalida no caminho da jogada");
			}
			path.add(square.asInt());
		}
		return path;
	}

	/** Casa a {@code n} passos na direcao dada, ou -1 se sair do tabuleiro. */
	private static int step(int square, int dr, int dc, int n) {
		int row = square / SIZE + dr * n;
		int col = square % SIZE + dc * n;
		return row < 0 || row >= SIZE || col < 0 || col >= SIZE ? -1 : row * SIZE + col;
	}

	private static boolean isFree(char[] board, int square, int origin) {
		return board[square] == EMPTY || square == origin;
	}

	private static boolean owns(char piece, int player) {
		return piece == MEN[player] || piece == KINGS[player];
	}

	private static boolean isOpponent(char piece, int player) {
		return owns(piece, 1 - player);
	}

	private static List<Integer> append(List<Integer> list, int value) {
		List<Integer> copy = new ArrayList<>(list);
		copy.add(value);
		return List.copyOf(copy);
	}

}
