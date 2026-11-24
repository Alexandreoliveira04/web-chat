package br.edu.webchat.call.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** O conteudo de {@code data} e gerado pelo navegador e repassado sem interpretacao. */
public record CallSignalRequest(
		@NotNull(message = "O tipo do sinal e obrigatorio")
		SignalType type,

		@NotNull(message = "O conteudo do sinal e obrigatorio")
		JsonNode data
) {
}
