package br.edu.webchat.game.engine.chess;

import br.edu.webchat.game.engine.GameEngine;
import br.edu.webchat.game.engine.GameOutcome;
import br.edu.webchat.game.entity.GameType;
import br.edu.webchat.shared.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveConversionException;
import com.github.bhlangonijr.chesslib.move.MoveList;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Xadrez, com as regras da biblioteca chesslib: roque, en passant, promocao, xeque-mate,
 * afogamento, repeticao tripla, material insuficiente e regra dos 50 lances.
 *
 * <p>A jogada e {@code {"move": "e2e4"}} em UCI; na promocao, a peca vem no fim
 * ({@code e7e8q}, {@code e7e8n}...). Toda jogada e conferida contra a lista de jogadas legais:
 * o {@code doMove} da biblioteca, mesmo com validacao, aceita jogadas impossiveis.
 */
@Component
public class ChessEngine implements GameEngine<ChessState> {

	@Override
	public GameType type() {
		return GameType.CHESS;
	}

	@Override
	public Class<ChessState> stateType() {
		return ChessState.class;
	}

	@Override
	public ChessState initialState() {
		return state(new Board(), List.of());
	}

	@Override
	public int playerToMove(ChessState state) {
		return state.toMove();
	}

	@Override
	public ChessState applyMove(ChessState state, int player, JsonNode move) {
		if (state.ending() != null) {
			throw new BadRequestException("A partida ja terminou");
		}
		if (player != state.toMove()) {
			throw new BadRequestException("Nao e a sua vez");
		}

		JsonNode node = move == null ? null : move.get("move");
		if (node == null || !node.isTextual()) {
			throw new BadRequestException("Informe a jogada em UCI, por exemplo {\"move\": \"e2e4\"}");
		}
		String uci = node.asText().trim().toLowerCase(Locale.ROOT);

		Board board = replay(state.history());
		Move legal = board.legalMoves().stream()
				.filter(candidate -> candidate.toString().equals(uci))
				.findFirst()
				.orElseThrow(() -> new BadRequestException("Jogada ilegal: " + uci));
		board.doMove(legal);

		List<String> history = new ArrayList<>(state.history());
		history.add(uci);
		return state(board, history);
	}

	@Override
	public GameOutcome outcome(ChessState state) {
		if (state.ending() == null) {
			return GameOutcome.ONGOING;
		}
		// No mate, perde quem ficou sem jogada: o lado que joga agora.
		return state.ending() == ChessEnding.CHECKMATE ? GameOutcome.win(1 - state.toMove()) : GameOutcome.DRAW;
	}

	private static ChessState state(Board board, List<String> history) {
		return new ChessState(
				board.getFen(),
				board.getSideToMove() == Side.WHITE ? 0 : 1,
				List.copyOf(history),
				san(history),
				board.legalMoves().stream().map(Move::toString).toList(),
				board.isKingAttacked(),
				ending(board));
	}

	private static ChessEnding ending(Board board) {
		if (board.isMated()) {
			return ChessEnding.CHECKMATE;
		}
		if (board.isStaleMate()) {
			return ChessEnding.STALEMATE;
		}
		if (board.isInsufficientMaterial()) {
			return ChessEnding.INSUFFICIENT_MATERIAL;
		}
		if (board.isRepetition()) {
			return ChessEnding.REPETITION;
		}
		if (board.getHalfMoveCounter() >= 100) {
			return ChessEnding.FIFTY_MOVES;
		}
		return null;
	}

	/** Refaz a partida desde a posicao inicial; o lado de cada jogada vem da propria vez. */
	private static Board replay(List<String> history) {
		Board board = new Board();
		for (String uci : history) {
			board.doMove(new Move(uci, board.getSideToMove()));
		}
		return board;
	}

	private static List<String> san(List<String> history) {
		if (history.isEmpty()) {
			return List.of();
		}

		MoveList moves = new MoveList();
		for (int i = 0; i < history.size(); i++) {
			moves.add(new Move(history.get(i), i % 2 == 0 ? Side.WHITE : Side.BLACK));
		}
		try {
			return Arrays.asList(moves.toSanArray());
		}
		catch (MoveConversionException ex) {
			throw new IllegalStateException("Historico de xadrez invalido", ex);
		}
	}

}
