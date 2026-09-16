package br.edu.webchat.chat.entity;

import java.io.Serializable;
import java.util.Objects;

public class ChatParticipantId implements Serializable {

	private Long chat;
	private Long user;

	public ChatParticipantId() {}

	public ChatParticipantId(Long chat, Long user) {
		this.chat = chat;
		this.user = user;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ChatParticipantId that)) {
			return false;
		}
		return Objects.equals(chat, that.chat) && Objects.equals(user, that.user);
	}

	@Override
	public int hashCode() {
		return Objects.hash(chat, user);
	}

}
