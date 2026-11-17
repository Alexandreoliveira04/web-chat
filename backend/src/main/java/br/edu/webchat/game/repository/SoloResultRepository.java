package br.edu.webchat.game.repository;

import br.edu.webchat.game.entity.SoloGame;
import br.edu.webchat.game.entity.SoloResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SoloResultRepository extends JpaRepository<SoloResult, Long> {

	/** Melhor tempo e numero de vitorias de cada pessoa, do mais rapido para o mais lento. */
	@Query("""
			select u.id as userId, u.name as name, u.avatarUrl as avatarUrl,
				min(r.durationMs) as bestDurationMs, count(r) as wins
			from SoloResult r join r.user u
			where r.game = :game and r.mode = :mode
			group by u.id, u.name, u.avatarUrl
			order by min(r.durationMs) asc, u.id asc
			""")
	List<BestTime> findBestTimes(@Param("game") SoloGame game, @Param("mode") String mode);

	@Query("""
			select min(r.durationMs) from SoloResult r
			where r.user.id = :userId and r.game = :game and r.mode = :mode
			""")
	Long findBestDuration(@Param("userId") Long userId, @Param("game") SoloGame game, @Param("mode") String mode);

	interface BestTime {

		Long getUserId();

		String getName();

		String getAvatarUrl();

		Long getBestDurationMs();

		Long getWins();

	}

}
