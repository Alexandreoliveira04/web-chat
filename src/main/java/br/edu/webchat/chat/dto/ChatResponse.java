package br.edu.webchat.chat.dto;

import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.Message;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record ChatResponse(
		Long id,
		List<ParticipantResponse> participants,
		MessageResponse lastMessage,
		long unreadCount,
		Instant createdAt,
		Instant updatedAt
) {

	public static ChatResponse from(Chat chat, Message lastMessage, long unreadCount) {
		List<ParticipantResponse> participants = chat.getParticipants().stream()
				.map(ParticipantResponse::from)
				.sorted(Comparator.comparing(ParticipantResponse::id))
				.toList();

		return new ChatResponse(
				chat.getId(),
				participants,
				lastMessage == null ? null : MessageResponse.from(lastMessage),
				unreadCount,
				chat.getCreatedAt(),
				chat.getUpdatedAt());
	}

}
