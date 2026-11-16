package br.edu.webchat.game.dto;

import br.edu.webchat.game.entity.GameType;
import jakarta.validation.constraints.NotNull;

/**
 * @param chatId conversa de onde saiu o desafio; opcional (desafio feito pela area de jogos)
 */
public record CreateGameRequest(
		@NotNull(message = "O jogo e obrigatorio")
		GameType type,

		@NotNull(message = "O adversario e obrigatorio")
		Long opponentId,

		Long chatId
) {
}
