package br.edu.webchat.game.engine;

/**
 * Situacao da partida segundo as regras do jogo.
 *
 * @param winner indice do vencedor (0 ou 1); nulo enquanto a partida corre ou em empate
 */
public record GameOutcome(boolean finished, Integer winner) {

	public static final GameOutcome ONGOING = new GameOutcome(false, null);
	public static final GameOutcome DRAW = new GameOutcome(true, null);

	public static GameOutcome win(int player) {
		return new GameOutcome(true, player);
	}

}
