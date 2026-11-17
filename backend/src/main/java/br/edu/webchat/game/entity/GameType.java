package br.edu.webchat.game.entity;

/** Jogos de duas pessoas. Cada tipo precisa de um {@code GameEngine} correspondente. */
public enum GameType {

	TIC_TAC_TOE(false),

	CHECKERS(true),

	CHESS(true);

	/** Partidas longas: tem relogio (ritmo escolhido no desafio) e aceitam proposta de empate. */
	private final boolean timed;

	GameType(boolean timed) {
		this.timed = timed;
	}

	public boolean isTimed() {
		return timed;
	}

}
