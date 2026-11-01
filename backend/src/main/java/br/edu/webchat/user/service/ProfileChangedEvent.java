package br.edu.webchat.user.service;

import br.edu.webchat.user.dto.ProfileResponse;

/** Publicado quando um usuario troca nome ou foto; o envio via WebSocket acontece apos o commit. */
public record ProfileChangedEvent(ProfileResponse profile) {
}
