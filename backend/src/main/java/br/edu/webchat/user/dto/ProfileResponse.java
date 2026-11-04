package br.edu.webchat.user.dto;

import br.edu.webchat.user.entity.User;

/** Dados publicos do perfil enviados a todos quando alguem troca nome ou foto. */
public record ProfileResponse(
		Long id,
		String name,
		String avatarUrl
) {

	public static ProfileResponse from(User user) {
		return new ProfileResponse(user.getId(), user.getName(), user.getAvatarUrl());
	}

}
