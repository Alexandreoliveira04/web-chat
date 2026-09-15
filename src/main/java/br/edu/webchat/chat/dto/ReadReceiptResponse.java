package br.edu.webchat.chat.dto;

import java.time.Instant;

public record ReadReceiptResponse(
		Long chatId,
		Long readerId,
		int markedAsRead,
		Instant readAt
) {
}
