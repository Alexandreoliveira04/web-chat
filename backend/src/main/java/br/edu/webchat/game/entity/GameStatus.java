package br.edu.webchat.game.entity;

public enum GameStatus {

	/** Desafio enviado, esperando o adversario aceitar ou recusar. */
	INVITED,

	IN_PROGRESS,

	/** Terminou por qualquer motivo; o motivo fica em {@link GameResult}. */
	FINISHED

}
