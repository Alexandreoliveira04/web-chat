package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.ChatEventResponse;

import java.util.List;

public record ChatChangedEvent(
		ChatEventResponse event,
		List<String> recipientEmails
) {
}
