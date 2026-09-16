package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.MessageResponse;

import java.util.List;

public record MessageUpdatedEvent(
		MessageResponse message,
		List<String> recipientEmails
) {
}
