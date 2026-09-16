package br.edu.webchat.chat.entity;

import br.edu.webchat.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "chat_participants")
@IdClass(ChatParticipantId.class)
public class ChatParticipant {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_id")
	private Chat chat;

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "last_read_message_id")
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Message lastReadMessage;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected ChatParticipant() {}

	public ChatParticipant(Chat chat, User user) {
		this.chat = chat;
		this.user = user;
	}

	@PrePersist
	void onCreate() {
		this.joinedAt = Instant.now();
	}

	public boolean hasRead(Long messageId) {
		Long lastRead = getLastReadMessageId();
		return lastRead != null && lastRead >= messageId;
	}

	public Chat getChat() {
		return chat;
	}

	public User getUser() {
		return user;
	}

	public Long getLastReadMessageId() {
		return lastReadMessage == null ? null : lastReadMessage.getId();
	}

	public void setLastReadMessage(Message lastReadMessage) {
		this.lastReadMessage = lastReadMessage;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

}
