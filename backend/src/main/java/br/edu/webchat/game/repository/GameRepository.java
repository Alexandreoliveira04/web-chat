package br.edu.webchat.game.repository;

import br.edu.webchat.game.entity.Game;
import br.edu.webchat.game.entity.GameStatus;
import br.edu.webchat.game.entity.GameType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

	@Query("""
			select g from Game g
			join fetch g.challenger
			join fetch g.opponent
			left join fetch g.winner
			where (g.challenger.id = :userId or g.opponent.id = :userId)
				and g.status in :statuses
			order by g.updatedAt desc, g.id desc
			""")
	List<Game> findByPlayer(@Param("userId") Long userId, @Param("statuses") Collection<GameStatus> statuses,
			Pageable pageable);

	/** Partidas em andamento cujo jogador da vez ja ficou sem tempo. */
	@Query("""
			select g.id from Game g
			where g.status = br.edu.webchat.game.entity.GameStatus.IN_PROGRESS
				and g.turnDeadlineAt <= :now
			""")
	List<Long> findOutOfTime(@Param("now") Instant now);

	@Query("""
			select count(g) > 0 from Game g
			where g.type = :type
				and g.status in :statuses
				and ((g.challenger.id = :first and g.opponent.id = :second)
					or (g.challenger.id = :second and g.opponent.id = :first))
			""")
	boolean existsBetween(@Param("type") GameType type, @Param("first") Long first, @Param("second") Long second,
			@Param("statuses") Collection<GameStatus> statuses);

}
