package br.edu.webchat.call.dto;

public record CallEventResponse(
		CallEventType type,
		Long chatId,
		Long callerId,
		Long calleeId,
		CallEndReason reason
) {
}
