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

	@Query("""
			select distinct c from Chat c
			join fetch c.participants p
			join fetch p.user
			where c.id in (
				select c2.id from Chat c2
				join c2.participants p2
				where p2.user.id = :userId)
			and (
			   lower(c.name) like lower(concat('%', :query, '%'))
			   or exists (
			       select 1 from ChatParticipant cp 
			       join cp.user u 
			       where cp.chat.id = c.id and lower(u.name) like lower(concat('%', :query, '%'))
			   )
			   or exists (
			       select 1 from Message m 
			       where m.chat.id = c.id and lower(m.content) like lower(concat('%', :query, '%'))
			   )
			)
			order by c.updatedAt desc, c.id desc
			""")
	List<Chat> searchAllByParticipantIdAndQuery(@Param("userId") Long userId, @Param("query") String query);

	@EntityGraph(attributePaths = {"participants", "participants.user"})
	Optional<Chat> findWithParticipantsById(Long id);

}
