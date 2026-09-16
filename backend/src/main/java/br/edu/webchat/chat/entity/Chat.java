package br.edu.webchat.chat.entity;

import br.edu.webchat.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "chats")
public class Chat {

	public static final int MAX_PARTICIPANTS = 50;
	public static final int MAX_NAME_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ChatType type;

	@Column(length = MAX_NAME_LENGTH)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private User owner;

	@Column(name = "direct_key", unique = true, updatable = false, length = 40)
	private String directKey;

	@OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ChatParticipant> participants = new HashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Chat() {}

	public Chat(User first, User second) {
		this.type = ChatType.DIRECT;
		this.directKey = directKeyOf(first.getId(), second.getId());
		this.participants.add(new ChatParticipant(this, first));
		this.participants.add(new ChatParticipant(this, second));
	}

	public static Chat group(String name, User owner) {
		Chat chat = new Chat();
		chat.type = ChatType.GROUP;
		chat.name = name;
		chat.owner = owner;
		chat.participants.add(new ChatParticipant(chat, owner));
		return chat;
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

	public boolean isGroup() {
		return type == ChatType.GROUP;
	}

	public boolean isOwner(Long userId) {
		return owner != null && owner.getId().equals(userId);
	}

	public boolean addParticipant(User user) {
		if (hasParticipant(user.getId())) {
			return false;
		}
		participants.add(new ChatParticipant(this, user));
		registerActivity();
		return true;
	}

	public boolean removeParticipant(Long userId) {
		boolean removed = participants.removeIf(participant -> participant.getUser().getId().equals(userId));
		if (removed) {
			registerActivity();
		}
		return removed;
	}

	public void rename(String name) {
		this.name = name;
		registerActivity();
	}

	public Optional<User> oldestParticipant() {
		return participants.stream()
				.map(ChatParticipant::getUser)
				.min(Comparator.comparing(User::getId));
	}

	public void transferOwnershipTo(User user) {
		this.owner = user;
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

	public ChatType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public Long getOwnerId() {
		return owner == null ? null : owner.getId();
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
