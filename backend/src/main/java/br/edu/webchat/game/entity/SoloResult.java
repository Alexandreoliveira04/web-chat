package br.edu.webchat.game.entity;

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
import jakarta.persistence.Table;

import java.time.Instant;

/** Uma vitoria em um jogo solo. So entra no ranking quem venceu. */
@Entity
@Table(name = "solo_results")
public class SoloResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private SoloGame game;

	@Column(nullable = false, updatable = false, length = 20)
	private String mode;

	@Column(name = "duration_ms", nullable = false, updatable = false)
	private Long durationMs;

	@Column(updatable = false)
	private Integer moves;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SoloResult() {}

	public SoloResult(User user, SoloGame game, String mode, long durationMs, Integer moves) {
		this.user = user;
		this.game = game;
		this.mode = mode;
		this.durationMs = durationMs;
		this.moves = moves;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public SoloGame getGame() {
		return game;
	}

	public String getMode() {
		return mode;
	}

	public Long getDurationMs() {
		return durationMs;
	}

	public Integer getMoves() {
		return moves;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
