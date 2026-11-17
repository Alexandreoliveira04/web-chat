package br.edu.webchat.game.entity;

public enum GameResult {

	/** Alguem venceu pelas regras do jogo. */
	WIN,

	DRAW,

	/** Os dois aceitaram empatar. */
	AGREED_DRAW,

	/** Um dos jogadores desistiu; o outro vence. */
	RESIGNATION,

	/** O tempo de um dos jogadores acabou; o outro vence. */
	TIMEOUT,

	/** O adversario recusou o desafio. */
	DECLINED,

	/** Quem desafiou desistiu antes de o adversario responder. */
	CANCELED

}
