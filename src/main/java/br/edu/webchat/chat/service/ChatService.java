package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ChatService {

	private final ChatRepository chatRepository;
	private final UserRepository userRepository;

	public ChatService(ChatRepository chatRepository, UserRepository userRepository) {
		this.chatRepository = chatRepository;
		this.userRepository = userRepository;
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
			return new CreateChatResult(ChatResponse.from(existing.get()), false);
		}

		try {
			return new CreateChatResult(ChatResponse.from(chatRepository.saveAndFlush(new Chat(me, other))), true);
		}
		catch (DataIntegrityViolationException ex) {
			return chatRepository.findByDirectKey(directKey)
					.map(created -> new CreateChatResult(ChatResponse.from(created), false))
					.orElseThrow(() -> ex);
		}
	}

	@Transactional(readOnly = true)
	public List<ChatResponse> findMyChats(String authenticatedEmail) {
		User me = findAuthenticatedUser(authenticatedEmail);

		return chatRepository.findAllByParticipantId(me.getId()).stream()
				.map(ChatResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ChatResponse findById(Long chatId, String authenticatedEmail) {
		User me = findAuthenticatedUser(authenticatedEmail);

		Chat chat = chatRepository.findWithParticipantsById(chatId)
				.orElseThrow(() -> new NotFoundException("Conversa nao encontrada: " + chatId));

		if (!chat.hasParticipant(me.getId())) {
			throw new ForbiddenException("Voce nao participa desta conversa");
		}

		return ChatResponse.from(chat);
	}

	private User findAuthenticatedUser(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase())
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + email));
	}

}
