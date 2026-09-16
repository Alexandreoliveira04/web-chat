package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {

	private final ChatRepository chatRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final ChatCreator chatCreator;

	public ChatService(ChatRepository chatRepository, MessageRepository messageRepository,
			UserRepository userRepository, ChatCreator chatCreator) {
		this.chatRepository = chatRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.chatCreator = chatCreator;
	}

	public CreateChatResult create(String authenticatedEmail, CreateChatRequest request) {
		User me = findAuthenticatedUser(authenticatedEmail);

		if (me.getId().equals(request.participantId())) {
			throw new BadRequestException("Nao e possivel iniciar uma conversa consigo mesmo");
		}

		User other = userRepository.findById(request.participantId())
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + request.participantId()));

		String directKey = Chat.directKeyOf(me.getId(), other.getId());

		Optional<Chat> existing = chatRepository.findByDirectKey(directKey);
		if (existing.isPresent()) {
			return new CreateChatResult(toResponse(existing.get(), me), false);
		}

		DataIntegrityViolationException conflict = null;
		try {
			chatCreator.createDirect(me.getId(), other.getId());
		}
		catch (DataIntegrityViolationException ex) {
			conflict = ex;
		}

		Optional<Chat> chat = chatRepository.findByDirectKey(directKey);
		if (chat.isEmpty()) {
			throw conflict == null ? new IllegalStateException("Conversa nao encontrada apos a criacao") : conflict;
		}

		return new CreateChatResult(toResponse(chat.get(), me), conflict == null);
	}

	@Transactional(readOnly = true)
	public List<ChatResponse> findMyChats(String authenticatedEmail) {
		User me = findAuthenticatedUser(authenticatedEmail);
		return toResponses(chatRepository.findAllByParticipantId(me.getId()), me);
	}

	@Transactional(readOnly = true)
	public ChatResponse findById(Long chatId, String authenticatedEmail) {
		User me = findAuthenticatedUser(authenticatedEmail);
		return toResponse(findParticipantChat(chatId, me), me);
	}

	Chat findParticipantChat(Long chatId, User user) {
		Chat chat = chatRepository.findWithParticipantsById(chatId)
				.orElseThrow(() -> new NotFoundException("Conversa nao encontrada: " + chatId));

		if (!chat.hasParticipant(user.getId())) {
			throw new ForbiddenException("Voce nao participa desta conversa");
		}

		return chat;
	}

	User findAuthenticatedUser(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase())
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + email));
	}

	ChatResponse toResponse(Chat chat, User me) {
		return toResponses(List.of(chat), me).get(0);
	}

	private List<ChatResponse> toResponses(List<Chat> chats, User me) {
		if (chats.isEmpty()) {
			return List.of();
		}

		List<Long> chatIds = chats.stream().map(Chat::getId).toList();

		Map<Long, Message> lastMessages = messageRepository.findLastMessagesOfChats(chatIds).stream()
				.collect(Collectors.toMap(message -> message.getChat().getId(), Function.identity()));

		Map<Long, Long> unreadCounts = messageRepository.countUnreadByChat(chatIds, me.getId()).stream()
				.collect(Collectors.toMap(MessageRepository.UnreadCount::getChatId, MessageRepository.UnreadCount::getTotal));

		return chats.stream()
				.map(chat -> ChatResponse.from(chat, me.getId(), lastMessages.get(chat.getId()),
						unreadCounts.getOrDefault(chat.getId(), 0L)))
				.toList();
	}

}
