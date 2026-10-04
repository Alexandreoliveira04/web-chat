package br.edu.webchat.chat.dto;

import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;

public record ParticipantResponse(
		Long id,
		String name,
		String email,
		UserStatus status,
		String avatarUrl
) {

	public static ParticipantResponse from(User user) {
		return new ParticipantResponse(user.getId(), user.getName(), user.getEmail(), user.getStatus(), user.getAvatarUrl());
	}

}
