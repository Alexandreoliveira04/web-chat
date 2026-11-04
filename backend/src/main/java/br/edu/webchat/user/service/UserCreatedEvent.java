package br.edu.webchat.user.service;

import br.edu.webchat.user.dto.UserResponse;

/** Publicado quando alguem se cadastra; o envio via WebSocket acontece apos o commit. */
public record UserCreatedEvent(UserResponse user) {
}
