package br.edu.webchat.chat.dto;

public record ReadReceiptResponse(
		Long chatId,
		Long readerId,
		Long lastReadMessageId,
		int markedAsRead
) {
}
