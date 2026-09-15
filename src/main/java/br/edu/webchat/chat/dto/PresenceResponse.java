package br.edu.webchat.chat.dto;

import br.edu.webchat.user.entity.UserStatus;

public record PresenceResponse(
		Long userId,
		UserStatus status
) {
}
