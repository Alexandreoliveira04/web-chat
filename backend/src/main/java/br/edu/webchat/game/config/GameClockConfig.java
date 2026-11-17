package br.edu.webchat.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Relogio das partidas. O {@link Clock} e um bean para que o tempo possa ser controlado nos
 * testes; o agendamento liga o {@code GameClockScheduler}, que encerra as partidas cujo tempo
 * acabou sem jogada.
 */
@Configuration
@EnableScheduling
public class GameClockConfig {

	@Bean
	public Clock gameClock() {
		return Clock.systemUTC();
	}

}
