package br.edu.webchat.game.entity;

import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.user.entity.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * Partida entre dois jogadores. Quem desafiou e o jogador 0 e o adversario e o jogador 1:
 * e por esse indice que os motores de jogo sabem de quem e a vez.
 *
 * <p>O {@link Version} protege contra duas jogadas simultaneas (duas abas, por exemplo):
 * a segunda falha com conflito em vez de sobrescrever a primeira.
 */
@Entity
@Table(name = "games")
public class Game {

	public static final int CHALLENGER = 0;
	public static final int OPPONENT = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private GameType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GameStatus status;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private GameResult result;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_id")
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Chat chat;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "challenger_id", nullable = false, updatable = false)
	private User challenger;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "opponent_id", nullable = false, updatable = false)
	private User opponent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "winner_id")
	private User winner;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String state;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected Game() {}

	public Game(GameType type, User challenger, User opponent, Chat chat, String initialState) {
		this.type = type;
		this.status = GameStatus.INVITED;
		this.challenger = challenger;
		this.opponent = opponent;
		this.chat = chat;
		this.state = initialState;
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

	/** 0 para quem desafiou, 1 para o adversario, -1 para quem nao joga esta partida. */
	public int playerIndexOf(Long userId) {
		if (challenger.getId().equals(userId)) {
			return CHALLENGER;
		}
		if (opponent.getId().equals(userId)) {
			return OPPONENT;
		}
		return -1;
	}

	public User player(int index) {
		return index == CHALLENGER ? challenger : opponent;
	}

	public boolean isActive() {
		return status != GameStatus.FINISHED;
	}

	public void start() {
		this.status = GameStatus.IN_PROGRESS;
	}

	public void changeState(String state) {
		this.state = state;
	}

	public void finish(GameResult result, User winner) {
		this.status = GameStatus.FINISHED;
		this.result = result;
		this.winner = winner;
		this.finishedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public GameType getType() {
		return type;
	}

	public GameStatus getStatus() {
		return status;
	}

	public GameResult getResult() {
		return result;
	}

	/** Le so o id: nao inicializa a conversa, que e LAZY. */
	public Long getChatId() {
		return chat == null ? null : chat.getId();
	}

	public User getChallenger() {
		return challenger;
	}

	public User getOpponent() {
		return opponent;
	}

	public User getWinner() {
		return winner;
	}

	public String getState() {
		return state;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

}
