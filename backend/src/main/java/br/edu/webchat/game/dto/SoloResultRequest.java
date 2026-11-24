package br.edu.webchat.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Vitoria enviada pelo navegador ao fim de um jogo solo.
 *
 * @param durationMs tempo da partida; abaixo de 1 segundo ou acima de 24 horas e recusado
 * @param moves numero de jogadas, quando o jogo conta (paciencia); opcional
 */
public record SoloResultRequest(
		@NotBlank(message = "O modo e obrigatorio")
		String mode,

		@NotNull(message = "O tempo e obrigatorio")
		@Min(value = 1_000, message = "Tempo minimo de 1 segundo")
		@Max(value = 86_400_000, message = "Tempo maximo de 24 horas")
		Long durationMs,

		@PositiveOrZero(message = "O numero de jogadas nao pode ser negativo")
		Integer moves
) {
}
