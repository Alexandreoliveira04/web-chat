package br.edu.webchat.chat.repository;

import br.edu.webchat.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

	@Query("""
			select m from Message m
			where m.chat.id = :chatId
			order by m.id desc
			""")
	List<Message> findLatest(@Param("chatId") Long chatId, Pageable pageable);

	@Query("""
			select m from Message m
			where m.chat.id = :chatId and m.id < :before
			order by m.id desc
			""")
	List<Message> findBefore(@Param("chatId") Long chatId, @Param("before") Long before, Pageable pageable);

	@Query("""
			select m from Message m
			where m.id in (
				select max(m2.id) from Message m2
				where m2.chat.id in :chatIds
				group by m2.chat.id)
			""")
	List<Message> findLastMessagesOfChats(@Param("chatIds") Collection<Long> chatIds);

	@Query("""
			select m.chat.id as chatId, count(m) as total from Message m
			where m.chat.id in :chatIds and m.sender.id <> :readerId and m.readAt is null
			group by m.chat.id
			""")
	List<UnreadCount> countUnreadByChat(@Param("chatIds") Collection<Long> chatIds, @Param("readerId") Long readerId);

	@Modifying
	@Query("""
			update Message m set m.readAt = :readAt
			where m.chat.id = :chatId and m.sender.id <> :readerId and m.readAt is null
			""")
	int markAsRead(@Param("chatId") Long chatId, @Param("readerId") Long readerId, @Param("readAt") Instant readAt);

	interface UnreadCount {

		Long getChatId();

		Long getTotal();

	}

}
