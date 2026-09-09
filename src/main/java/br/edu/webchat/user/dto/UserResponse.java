package br.edu.webchat.user.dto;

import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;

import java.time.Instant;

public record UserResponse(
		Long id,
		String name,
		String email,
		UserStatus status,
		Instant createdAt,
		Instant updatedAt
) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getStatus(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}

}
