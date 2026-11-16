package br.edu.webchat.game.dto;

import br.edu.webchat.game.entity.GameResult;
import br.edu.webchat.game.entity.GameStatus;
import br.edu.webchat.game.entity.GameType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Partida como os jogadores a veem. E a mesma para os dois, e e tambem o que chega em
 * tempo real a cada mudanca.
 *
 * @param challenger jogador 0, quem fez o desafio
 * @param opponent jogador 1, quem foi desafiado
 * @param turnPlayerId de quem e a vez; nulo fora de {@code IN_PROGRESS}
 * @param state tabuleiro no formato do jogo ({@code type})
 */
public record GameResponse(
		Long id,
		GameType type,
		GameStatus status,
		GameResult result,
		Long chatId,
		PlayerResponse challenger,
		PlayerResponse opponent,
		Long turnPlayerId,
		Long winnerId,
		JsonNode state,
		Instant createdAt,
		Instant updatedAt,
		Instant finishedAt
) {
}
