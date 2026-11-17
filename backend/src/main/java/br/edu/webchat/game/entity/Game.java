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

import java.time.Duration;
import java.time.Instant;

/**
 * Partida entre dois jogadores. Quem desafiou e o jogador 0 e o adversario e o jogador 1:
 * e por esse indice que os motores de jogo sabem de quem e a vez.
 *
 * <p>O {@link Version} protege contra duas jogadas simultaneas (duas abas, por exemplo):
 * a segunda falha com conflito em vez de sobrescrever a primeira.
 *
 * <p>Relogio (so nos jogos com ritmo): {@code challengerTimeMs}/{@code opponentTimeMs} e o
 * tempo que cada um tinha quando a vez atual comecou, em {@code turnStartedAt}. O tempo de
 * quem joga corre a partir dai e e descontado quando a vez termina.
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

	@Column(name = "time_control_ms")
	private Long timeControlMs;

	@Column(name = "challenger_time_ms")
	private Long challengerTimeMs;

	@Column(name = "opponent_time_ms")
	private Long opponentTimeMs;

	@Column(name = "turn_started_at")
	private Instant turnStartedAt;

	/** Inicio da vez + tempo de quem joga: quando passar, a partida acaba por tempo. */
	@Column(name = "turn_deadline_at")
	private Instant turnDeadlineAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "draw_offered_by")
	private User drawOfferedBy;

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
		this.drawOfferedBy = null;
		this.turnStartedAt = null;
		this.turnDeadlineAt = null;
	}

	// ---------- relogio ----------

	/** Da a cada jogador o tempo do ritmo escolhido. */
	public void useTimeControl(Duration perPlayer) {
		long millis = perPlayer.toMillis();
		this.timeControlMs = millis;
		this.challengerTimeMs = millis;
		this.opponentTimeMs = millis;
	}

	public boolean isTimed() {
		return timeControlMs != null;
	}

	/** Comeca a vez de {@code player}: o tempo dele passa a correr. */
	public void startTurn(int player, Instant now) {
		if (!isTimed()) {
			return;
		}
		this.turnStartedAt = now;
		this.turnDeadlineAt = now.plusMillis(storedTimeLeft(player));
	}

	/**
	 * Termina a vez de {@code player}, descontando o tempo que ela durou.
	 *
	 * @return false se o tempo dele acabou antes de {@code now}
	 */
	public boolean endTurn(int player, Instant now) {
		if (!isTimed() || turnStartedAt == null) {
			return true;
		}
		long left = storedTimeLeft(player) - Duration.between(turnStartedAt, now).toMillis();
		setStoredTimeLeft(player, Math.max(0, left));
		this.turnStartedAt = null;
		this.turnDeadlineAt = null;
		return left > 0;
	}

	/** Tempo de {@code player} em {@code now}, contando a vez em andamento se for a dele. */
	public long timeLeft(int player, int playerToMove, Instant now) {
		long left = storedTimeLeft(player);
		if (player == playerToMove && turnStartedAt != null) {
			left -= Duration.between(turnStartedAt, now).toMillis();
		}
		return Math.max(0, left);
	}

	public boolean isOutOfTime(Instant now) {
		return turnDeadlineAt != null && !now.isBefore(turnDeadlineAt);
	}

	private long storedTimeLeft(int player) {
		return player == CHALLENGER ? challengerTimeMs : opponentTimeMs;
	}

	private void setStoredTimeLeft(int player, long millis) {
		if (player == CHALLENGER) {
			this.challengerTimeMs = millis;
		}
		else {
			this.opponentTimeMs = millis;
		}
	}

	// ---------- proposta de empate ----------

	public void offerDraw(User player) {
		this.drawOfferedBy = player;
	}

	public void clearDrawOffer() {
		this.drawOfferedBy = null;
	}

	/** Le so o id: nao inicializa o usuario, que e LAZY. */
	public Long getDrawOfferedById() {
		return drawOfferedBy == null ? null : drawOfferedBy.getId();
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

	public Long getTimeControlMs() {
		return timeControlMs;
	}

	public Instant getTurnStartedAt() {
		return turnStartedAt;
	}

	public Instant getTurnDeadlineAt() {
		return turnDeadlineAt;
	}

}
