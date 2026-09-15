package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.MarkAsReadResponse;
import br.edu.webchat.chat.dto.MessageHistoryResponse;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MessageService {

	private final ChatService chatService;
	private final MessageRepository messageRepository;

	public MessageService(ChatService chatService, MessageRepository messageRepository) {
		this.chatService = chatService;
		this.messageRepository = messageRepository;
	}

	@Transactional
	public MessageResponse send(Long chatId, String authenticatedEmail, SendMessageRequest request) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		Chat chat = chatService.findParticipantChat(chatId, me);

		Message message = messageRepository.save(new Message(chat, me, request.content().strip()));
		chat.registerActivity();

		return MessageResponse.from(message);
	}

	@Transactional(readOnly = true)
	public MessageHistoryResponse findHistory(Long chatId, String authenticatedEmail, Long before, int size) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		chatService.findParticipantChat(chatId, me);

		Pageable onePageMore = PageRequest.ofSize(size + 1);
		List<Message> newestFirst = new ArrayList<>(before == null
				? messageRepository.findLatest(chatId, onePageMore)
				: messageRepository.findBefore(chatId, before, onePageMore));

		boolean hasMore = newestFirst.size() > size;
		if (hasMore) {
			newestFirst.remove(newestFirst.size() - 1);
		}

		Collections.reverse(newestFirst);
		List<MessageResponse> messages = newestFirst.stream().map(MessageResponse::from).toList();

		Long nextBefore = hasMore ? messages.get(0).id() : null;
		return new MessageHistoryResponse(messages, hasMore, nextBefore);
	}

	@Transactional
	public MarkAsReadResponse markAsRead(Long chatId, String authenticatedEmail) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		chatService.findParticipantChat(chatId, me);

		return new MarkAsReadResponse(messageRepository.markAsRead(chatId, me.getId(), Instant.now()));
	}

}
