package br.edu.webchat.game.service;

import br.edu.webchat.game.dto.GameResponse;

import java.util.List;

/** Publicado a cada mudanca de partida; vai para os jogadores depois do commit. */
public record GameChangedEvent(
		GameResponse game,
		List<String> recipientEmails
) {
}
