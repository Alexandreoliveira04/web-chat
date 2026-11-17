package br.edu.webchat.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Encerra por tempo as partidas em que o jogador da vez deixou o relogio zerar sem jogar.
 * Quem joga a tempo tem o relogio conferido na propria jogada; este verificador cobre quem
 * simplesmente parou de jogar (ou fechou a aba).
 */
@Component
public class GameClockScheduler {

	private static final Logger log = LoggerFactory.getLogger(GameClockScheduler.class);

	private final GameService gameService;
	private final Clock clock;

	public GameClockScheduler(GameService gameService, Clock clock) {
		this.gameService = gameService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.games.clock-check-ms:1000}")
	public void expireOutOfTimeGames() {
		Instant now = clock.instant();
		for (Long gameId : gameService.findOutOfTime(now)) {
			try {
				gameService.expireIfOutOfTime(gameId, now);
			}
			catch (OptimisticLockingFailureException ex) {
				// Uma jogada chegou ao mesmo tempo; a proxima verificacao confere de novo.
				log.debug("Partida {} mudou durante a verificacao de tempo", gameId);
			}
		}
	}

}
