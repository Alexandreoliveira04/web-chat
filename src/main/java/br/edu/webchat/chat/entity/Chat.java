package br.edu.webchat.chat.entity;

import br.edu.webchat.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "chats")
public class Chat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "direct_key", nullable = false, unique = true, updatable = false, length = 40)
	private String directKey;

	@OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ChatParticipant> participants = new HashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Chat() {}

	public Chat(User first, User second) {
		this.directKey = directKeyOf(first.getId(), second.getId());
		this.participants.add(new ChatParticipant(this, first));
		this.participants.add(new ChatParticipant(this, second));
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

	public void registerActivity() {
		this.updatedAt = Instant.now();
	}

	public boolean hasParticipant(Long userId) {
		return participantOf(userId).isPresent();
	}

	public Optional<ChatParticipant> participantOf(Long userId) {
		return participants.stream()
				.filter(participant -> participant.getUser().getId().equals(userId))
				.findFirst();
	}

	public List<User> users() {
		return participants.stream().map(ChatParticipant::getUser).toList();
	}

	/**
	 * Menor marcador de leitura entre os outros participantes: mensagens ate esse id foram
	 * lidas por todos eles. Nulo quando algum ainda nao leu nada ou nao ha outro participante.
	 */
	public Long lastReadByOthers(Long userId) {
		Long minimum = null;

		for (ChatParticipant participant : participants) {
			if (participant.getUser().getId().equals(userId)) {
				continue;
			}
			Long lastRead = participant.getLastReadMessageId();
			if (lastRead == null) {
				return null;
			}
			minimum = minimum == null ? lastRead : Math.min(minimum, lastRead);
		}

		return minimum;
	}

	public Long getId() {
		return id;
	}

	public String getDirectKey() {
		return directKey;
	}

	public Set<ChatParticipant> getParticipants() {
		return participants;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
