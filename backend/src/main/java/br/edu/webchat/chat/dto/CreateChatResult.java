package br.edu.webchat.chat.dto;

public record CreateChatResult(
		ChatResponse chat,
		boolean created
) {
}
