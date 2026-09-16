package br.edu.webchat.chat.entity;

import br.edu.webchat.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

	public static final int MAX_CONTENT_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "chat_id", nullable = false, updatable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Chat chat;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false, updatable = false)
	private User sender;

	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "edited_at")
	private Instant editedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Message() {}

	public Message(Chat chat, User sender, String content) {
		this.chat = chat;
		this.sender = sender;
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public boolean isFrom(Long userId) {
		return sender.getId().equals(userId);
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void edit(String content) {
		this.content = content;
		this.editedAt = Instant.now();
	}

	public void markAsDeleted() {
		this.content = "";
		this.deletedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Chat getChat() {
		return chat;
	}

	public User getSender() {
		return sender;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getEditedAt() {
		return editedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
