package br.edu.webchat.game.dto;

/**
 * Relogio da partida no momento da resposta.
 *
 * @param initialMs tempo de cada jogador no inicio (o ritmo)
 * @param challengerMs tempo restante de quem desafiou
 * @param opponentMs tempo restante do adversario
 * @param runningPlayerId de quem e o tempo que esta correndo; nulo com a partida parada.
 *     O frontend desconta desse lado o tempo passado desde que recebeu a resposta
 */
public record ClockResponse(
		long initialMs,
		long challengerMs,
		long opponentMs,
		Long runningPlayerId
) {
}
