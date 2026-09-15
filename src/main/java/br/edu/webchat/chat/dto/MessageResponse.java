package br.edu.webchat.chat.dto;

import br.edu.webchat.chat.entity.Message;

import java.time.Instant;

public record MessageResponse(
		Long id,
		Long chatId,
		Long senderId,
		String content,
		Instant createdAt,
		Instant readAt
) {

	public static MessageResponse from(Message message) {
		return new MessageResponse(
				message.getId(),
				message.getChat().getId(),
				message.getSender().getId(),
				message.getContent(),
				message.getCreatedAt(),
				message.getReadAt());
	}

}
