package br.edu.webchat.game.dto;

import br.edu.webchat.game.entity.SoloGame;

import java.util.List;

/**
 * Ranking de um jogo solo em um modo: o melhor tempo de cada pessoa.
 *
 * @param top as primeiras posicoes
 * @param me a posicao de quem consultou, mesmo fora do {@code top}; nulo se ainda nao venceu
 */
public record SoloRankingResponse(
		SoloGame game,
		String mode,
		List<Entry> top,
		Entry me
) {

	public record Entry(
			int position,
			PlayerResponse player,
			long bestDurationMs,
			long wins
	) {
	}

}
