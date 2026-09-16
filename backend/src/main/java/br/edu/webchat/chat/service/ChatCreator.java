package br.edu.webchat.chat.service;

import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ChatCreator {

	private final ChatRepository chatRepository;
	private final UserRepository userRepository;

	ChatCreator(ChatRepository chatRepository, UserRepository userRepository) {
		this.chatRepository = chatRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	void createDirect(Long userId, Long otherUserId) {
		chatRepository.saveAndFlush(new Chat(
				userRepository.getReferenceById(userId),
				userRepository.getReferenceById(otherUserId)));
	}

}
