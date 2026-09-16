package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.EditMessageRequest;
import br.edu.webchat.chat.dto.MarkAsReadResponse;
import br.edu.webchat.chat.dto.MessageHistoryResponse;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.ReadReceiptResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.ChatParticipant;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MessageService {

	private final ChatService chatService;
	private final MessageRepository messageRepository;
	private final ApplicationEventPublisher events;

	public MessageService(ChatService chatService, MessageRepository messageRepository,
			ApplicationEventPublisher events) {
		this.chatService = chatService;
		this.messageRepository = messageRepository;
		this.events = events;
	}

	@Transactional
	public MessageResponse send(Long chatId, String authenticatedEmail, SendMessageRequest request) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		Chat chat = chatService.findParticipantChat(chatId, me);

		Message message = messageRepository.save(new Message(chat, me, request.content().strip()));
		chat.registerActivity();

		MessageResponse response = MessageResponse.from(message);
		events.publishEvent(new MessageSentEvent(response, participantEmails(chat)));
		return response;
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
	public MessageResponse edit(Long chatId, Long messageId, String authenticatedEmail, EditMessageRequest request) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		Chat chat = chatService.findParticipantChat(chatId, me);
		Message message = findOwnMessage(chat, messageId, me);

		if (message.isDeleted()) {
			throw new ConflictException("Nao e possivel editar uma mensagem apagada: " + messageId);
		}

		message.edit(request.content().strip());
		return publishUpdate(message, chat);
	}

	@Transactional
	public MessageResponse delete(Long chatId, Long messageId, String authenticatedEmail) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		Chat chat = chatService.findParticipantChat(chatId, me);
		Message message = findOwnMessage(chat, messageId, me);

		if (message.isDeleted()) {
			return MessageResponse.from(message);
		}

		message.markAsDeleted();
		return publishUpdate(message, chat);
	}

	private Message findOwnMessage(Chat chat, Long messageId, User me) {
		Message message = messageRepository.findById(messageId)
				.filter(candidate -> candidate.getChat().getId().equals(chat.getId()))
				.orElseThrow(() -> new NotFoundException("Mensagem nao encontrada: " + messageId));

		if (!message.isFrom(me.getId())) {
			throw new ForbiddenException("Somente o autor pode editar ou apagar a mensagem");
		}

		return message;
	}

	private MessageResponse publishUpdate(Message message, Chat chat) {
		MessageResponse response = MessageResponse.from(message);
		events.publishEvent(new MessageUpdatedEvent(response, participantEmails(chat)));
		return response;
	}

	@Transactional
	public MarkAsReadResponse markAsRead(Long chatId, String authenticatedEmail) {
		User me = chatService.findAuthenticatedUser(authenticatedEmail);
		Chat chat = chatService.findParticipantChat(chatId, me);
		ChatParticipant participant = chat.participantOf(me.getId()).orElseThrow();

		Long lastMessageId = messageRepository.findLastMessageId(chatId);
		if (lastMessageId == null || participant.hasRead(lastMessageId)) {
			return new MarkAsReadResponse(0);
		}

		Long previous = participant.getLastReadMessageId();
		int marked = messageRepository.countUnread(chatId, me.getId(), previous == null ? 0 : previous, lastMessageId);
		participant.setLastReadMessage(messageRepository.getReferenceById(lastMessageId));

		if (marked > 0) {
			ReadReceiptResponse receipt = new ReadReceiptResponse(chatId, me.getId(), lastMessageId, marked);
			events.publishEvent(new MessagesReadEvent(receipt, participantEmails(chat)));
		}

		return new MarkAsReadResponse(marked);
	}

	private static List<String> participantEmails(Chat chat) {
		return chat.users().stream().map(User::getEmail).sorted().toList();
	}

}
