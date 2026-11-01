package br.edu.webchat.call.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CallSignalResponse(
		Long chatId,
		Long fromId,
		SignalType type,
		JsonNode data
) {
}
