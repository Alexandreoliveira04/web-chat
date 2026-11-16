package br.edu.webchat.game.engine.tictactoe;

import br.edu.webchat.game.engine.GameEngine;
import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.game.entity.GameType;
import br.edu.webchat.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** Jogo da velha. A jogada e {@code {"cell": 0..8}}, contando da esquerda para a direita e de cima para baixo. */
@Component
public class TicTacToeEngine implements GameEngine<TicTacToeState> {

	static final char EMPTY = '.';
	private static final char[] SYMBOLS = {'X', 'O'};
	private static final int[][] LINES = {
			{0, 1, 2}, {3, 4, 5}, {6, 7, 8},
			{0, 3, 6}, {1, 4, 7}, {2, 5, 8},
			{0, 4, 8}, {2, 4, 6},
	};

	@Override
	public GameType type() {
		return GameType.TIC_TAC_TOE;
	}

	@Override
	public Class<TicTacToeState> stateType() {
		return TicTacToeState.class;
	}

	@Override
	public TicTacToeState initialState() {
		return new TicTacToeState(String.valueOf(EMPTY).repeat(9), 0);
	}

	@Override
	public int playerToMove(TicTacToeState state) {
		return state.toMove();
	}

	@Override
	public TicTacToeState applyMove(TicTacToeState state, int player, JsonNode move) {
		if (outcome(state).finished()) {
			throw new BadRequestException("A partida ja terminou");
		}
		if (player != state.toMove()) {
			throw new BadRequestException("Nao e a sua vez");
		}

		JsonNode cellNode = move == null ? null : move.get("cell");
		if (cellNode == null || !cellNode.isInt() || cellNode.asInt() < 0 || cellNode.asInt() > 8) {
			throw new BadRequestException("Informe a casa da jogada, de 0 a 8");
		}

		int cell = cellNode.asInt();
		char[] board = state.board().toCharArray();
		if (board[cell] != EMPTY) {
			throw new BadRequestException("Essa casa ja esta ocupada");
		}

		board[cell] = SYMBOLS[player];
		return new TicTacToeState(new String(board), 1 - player);
	}

	@Override
	public GameOutcome outcome(TicTacToeState state) {
		String board = state.board();

		for (int[] line : LINES) {
			char first = board.charAt(line[0]);
			if (first != EMPTY && first == board.charAt(line[1]) && first == board.charAt(line[2])) {
				return GameOutcome.win(first == SYMBOLS[0] ? 0 : 1);
			}
		}

		return board.indexOf(EMPTY) < 0 ? GameOutcome.DRAW : GameOutcome.ONGOING;
	}

}
