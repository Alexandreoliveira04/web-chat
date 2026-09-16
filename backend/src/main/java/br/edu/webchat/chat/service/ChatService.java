package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.AddParticipantRequest;
import br.edu.webchat.chat.dto.ChatEventResponse;
import br.edu.webchat.chat.dto.ChatEventResponse.ChatEventType;
import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.dto.CreateGroupRequest;
import br.edu.webchat.chat.dto.RenameChatRequest;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {

	private final ChatRepository chatRepository;
	private final MessageRepository messageRepository;
	private final UserRepository userRepository;
	private final ChatCreator chatCreator;
	private final ApplicationEventPublisher events;

	public ChatService(ChatRepository chatRepository, MessageRepository messageRepository,
			UserRepository userRepository, ChatCreator chatCreator, ApplicationEventPublisher events) {
		this.chatRepository = chatRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.chatCreator = chatCreator;
		this.events = events;
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

	@Transactional
	public ChatResponse createGroup(String authenticatedEmail, CreateGroupRequest request) {
		User me = findAuthenticatedUser(authenticatedEmail);

		Set<Long> memberIds = new LinkedHashSet<>(request.participantIds());
		memberIds.remove(me.getId());
		if (memberIds.isEmpty()) {
			throw new BadRequestException("Um grupo precisa de pelo menos mais um participante");
		}

		List<User> members = userRepository.findAllById(memberIds);
		if (members.size() != memberIds.size()) {
			throw new NotFoundException("Usuario nao encontrado entre os participantes informados");
		}

		Chat chat = Chat.group(request.name().strip(), me);
		members.forEach(chat::addParticipant);
		chatRepository.saveAndFlush(chat);

		publish(ChatEventType.CREATED, chat, chat.users());
		return toResponse(chat, me);
	}

	@Transactional
	public ChatResponse rename(Long chatId, String authenticatedEmail, RenameChatRequest request) {
		User me = findAuthenticatedUser(authenticatedEmail);
		Chat chat = findOwnedGroup(chatId, me);

		chat.rename(request.name().strip());

		publish(ChatEventType.UPDATED, chat, chat.users());
		return toResponse(chat, me);
	}

	@Transactional
	public ChatResponse addParticipant(Long chatId, String authenticatedEmail, AddParticipantRequest request) {
		User me = findAuthenticatedUser(authenticatedEmail);
		Chat chat = findOwnedGroup(chatId, me);

		User newParticipant = userRepository.findById(request.userId())
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + request.userId()));

		if (chat.getParticipants().size() >= Chat.MAX_PARTICIPANTS) {
			throw new BadRequestException("O grupo ja tem o maximo de " + Chat.MAX_PARTICIPANTS + " participantes");
		}
		if (!chat.addParticipant(newParticipant)) {
			throw new ConflictException("O usuario ja participa deste grupo: " + request.userId());
		}

		publish(ChatEventType.CREATED, chat, List.of(newParticipant));
		publish(ChatEventType.UPDATED, chat, chat.users().stream()
				.filter(user -> !user.getId().equals(newParticipant.getId()))
				.toList());
		return toResponse(chat, me);
	}

	@Transactional
	public ChatResponse removeParticipant(Long chatId, String authenticatedEmail, Long participantId) {
		User me = findAuthenticatedUser(authenticatedEmail);
		Chat chat = findOwnedGroup(chatId, me);

		if (chat.isOwner(participantId)) {
			throw new BadRequestException("O dono nao pode ser removido do grupo; use sair do grupo");
		}

		User removed = chat.participantOf(participantId)
				.orElseThrow(() -> new NotFoundException("Usuario nao participa deste grupo: " + participantId))
				.getUser();
		chat.removeParticipant(participantId);

		publish(ChatEventType.REMOVED, chat, List.of(removed));
		publish(ChatEventType.UPDATED, chat, chat.users());
		return toResponse(chat, me);
	}

	@Transactional
	public void leave(Long chatId, String authenticatedEmail) {
		User me = findAuthenticatedUser(authenticatedEmail);
		Chat chat = findParticipantChat(chatId, me);
		requireGroup(chat);

		chat.removeParticipant(me.getId());

		if (chat.getParticipants().isEmpty()) {
			chatRepository.delete(chat);
		}
		else if (chat.isOwner(me.getId())) {
			chat.transferOwnershipTo(chat.oldestParticipant().orElseThrow());
		}

		publish(ChatEventType.REMOVED, chat, List.of(me));
		publish(ChatEventType.UPDATED, chat, chat.users());
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

	private Chat findOwnedGroup(Long chatId, User user) {
		Chat chat = findParticipantChat(chatId, user);
		requireGroup(chat);

		if (!chat.isOwner(user.getId())) {
			throw new ForbiddenException("Somente quem criou o grupo pode administra-lo");
		}

		return chat;
	}

	private static void requireGroup(Chat chat) {
		if (!chat.isGroup()) {
			throw new BadRequestException("Esta operacao so vale para grupos");
		}
	}

	private void publish(ChatEventType type, Chat chat, List<User> recipients) {
		if (recipients.isEmpty()) {
			return;
		}

		List<String> emails = recipients.stream().map(User::getEmail).sorted().toList();
		events.publishEvent(new ChatChangedEvent(new ChatEventResponse(type, chat.getId()), emails));
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
