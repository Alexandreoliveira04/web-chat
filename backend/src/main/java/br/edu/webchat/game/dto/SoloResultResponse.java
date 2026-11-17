package br.edu.webchat.game.dto;

/**
 * @param personalBest a vitoria bateu o recorde pessoal (ou foi a primeira)
 * @param ranking o ranking ja com a vitoria registrada
 */
public record SoloResultResponse(
		long durationMs,
		boolean personalBest,
		SoloRankingResponse ranking
) {
}
