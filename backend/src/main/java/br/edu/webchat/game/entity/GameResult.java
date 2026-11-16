package br.edu.webchat.game.entity;

public enum GameResult {

	/** Alguem venceu pelas regras do jogo. */
	WIN,

	DRAW,

	/** Um dos jogadores desistiu; o outro vence. */
	RESIGNATION,

	/** O adversario recusou o desafio. */
	DECLINED,

	/** Quem desafiou desistiu antes de o adversario responder. */
	CANCELED

}
