package br.edu.webchat.user.dto;

import br.edu.webchat.user.entity.Role;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;

import java.time.Instant;

public record UserResponse(
		Long id,
		String name,
		String email,
		UserStatus status,
		Role role,
		Instant createdAt,
		Instant updatedAt,
		String avatarUrl
) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getStatus(),
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt(),
				user.getAvatarUrl());
	}

}
