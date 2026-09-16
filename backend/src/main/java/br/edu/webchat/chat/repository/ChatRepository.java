package br.edu.webchat.chat.repository;

import br.edu.webchat.chat.entity.Chat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

	@EntityGraph(attributePaths = {"participants", "participants.user"})
	Optional<Chat> findByDirectKey(String directKey);

	@Query("""
			select c from Chat c
			join fetch c.participants p
			join fetch p.user
			where c.id in (
				select c2.id from Chat c2
				join c2.participants p2
				where p2.user.id = :userId)
			order by c.updatedAt desc, c.id desc
			""")
	List<Chat> findAllByParticipantId(@Param("userId") Long userId);

	@EntityGraph(attributePaths = {"participants", "participants.user"})
	Optional<Chat> findWithParticipantsById(Long id);

}
