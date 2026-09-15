package br.edu.webchat.chat.entity;

import br.edu.webchat.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "chats")
public class Chat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "direct_key", nullable = false, unique = true, updatable = false, length = 40)
	private String directKey;

	@ManyToMany
	@JoinTable(
			name = "chat_participants",
			joinColumns = @JoinColumn(name = "chat_id"),
			inverseJoinColumns = @JoinColumn(name = "user_id"))
	private Set<User> participants = new HashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Chat() {}

	public Chat(User first, User second) {
		this.directKey = directKeyOf(first.getId(), second.getId());
		this.participants.add(first);
		this.participants.add(second);
	}

	public static String directKeyOf(Long userId, Long otherUserId) {
		return Math.min(userId, otherUserId) + ":" + Math.max(userId, otherUserId);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public boolean hasParticipant(Long userId) {
		return participants.stream().anyMatch(user -> user.getId().equals(userId));
	}

	public Long getId() {
		return id;
	}

	public String getDirectKey() {
		return directKey;
	}

	public Set<User> getParticipants() {
		return participants;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
