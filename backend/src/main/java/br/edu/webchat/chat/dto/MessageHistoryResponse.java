package br.edu.webchat.chat.dto;

import java.util.List;

public record MessageHistoryResponse(
		List<MessageResponse> messages,
		boolean hasMore,
		Long nextBefore
) {
}
