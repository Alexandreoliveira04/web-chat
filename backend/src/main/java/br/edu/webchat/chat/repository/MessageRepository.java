package br.edu.webchat.chat.repository;

import br.edu.webchat.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	@Query("select max(m.id) from Message m where m.chat.id = :chatId")
	Long findLastMessageId(@Param("chatId") Long chatId);

	@Query("""
			select m.chat.id as chatId, count(m) as total
			from Message m, ChatParticipant p
			where p.chat.id = m.chat.id and p.user.id = :readerId
				and m.chat.id in :chatIds
				and m.sender.id <> :readerId
				and (p.lastReadMessage is null or m.id > p.lastReadMessage.id)
			group by m.chat.id
			""")
	List<UnreadCount> countUnreadByChat(@Param("chatIds") Collection<Long> chatIds, @Param("readerId") Long readerId);

	@Query("""
			select count(m) from Message m
			where m.chat.id = :chatId
				and m.sender.id <> :readerId
				and m.id > :lastReadMessageId
				and m.id <= :upToMessageId
			""")
	int countUnread(@Param("chatId") Long chatId, @Param("readerId") Long readerId,
			@Param("lastReadMessageId") long lastReadMessageId, @Param("upToMessageId") long upToMessageId);

	interface UnreadCount {

		Long getChatId();

		Long getTotal();

	}

}
