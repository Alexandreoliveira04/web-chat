package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.ReadReceiptResponse;

import java.util.List;

public record MessagesReadEvent(
		ReadReceiptResponse receipt,
		List<String> recipientEmails
) {
}
