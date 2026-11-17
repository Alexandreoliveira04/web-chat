package br.edu.webchat.game.dto;

import br.edu.webchat.game.entity.GameType;
import jakarta.validation.constraints.NotNull;

/**
 * @param chatId conversa de onde saiu o desafio; opcional (desafio feito pela area de jogos)
 * @param timeControlMinutes ritmo: minutos de relogio para cada jogador (3, 5 ou 10).
 *     Obrigatorio nos jogos com relogio (xadrez, damas); nao vale nos outros
 */
public record CreateGameRequest(
		@NotNull(message = "O jogo e obrigatorio")
		GameType type,

		@NotNull(message = "O adversario e obrigatorio")
		Long opponentId,

		Long chatId,

		Integer timeControlMinutes
) {

	public CreateGameRequest(GameType type, Long opponentId, Long chatId) {
		this(type, opponentId, chatId, null);
	}

}
