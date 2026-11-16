package br.edu.webchat.game.dto;

import br.edu.webchat.user.entity.User;

public record PlayerResponse(
		Long id,
		String name,
		String avatarUrl
) {

	public static PlayerResponse from(User user) {
		return new PlayerResponse(user.getId(), user.getName(), user.getAvatarUrl());
	}

}
