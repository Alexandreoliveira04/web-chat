package br.edu.webchat.game.engine.chess;

public enum ChessEnding {

	CHECKMATE,

	/** Afogamento: quem joga nao esta em xeque e nao tem jogada legal. */
	STALEMATE,

	/** A mesma posicao pela terceira vez. */
	REPETITION,

	/** Ninguem tem pecas para dar mate (ex.: rei contra rei). */
	INSUFFICIENT_MATERIAL,

	/** 50 lances de cada lado sem captura nem movimento de peao. */
	FIFTY_MOVES

}
