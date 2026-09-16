package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.dto.ParticipantResponse;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	@Mock
	private ChatRepository chatRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ChatCreator chatCreator;

	@Mock
	private ApplicationEventPublisher events;

	private ChatService chatService;

	private final User alexandre = usuario(1L, "Alexandre", "alexandre@email.com");
	private final User maria = usuario(2L, "Maria", "maria@email.com");
	private final User joao = usuario(3L, "Joao", "joao@email.com");

	@BeforeEach
	void setUp() {
		chatService = new ChatService(chatRepository, messageRepository, userRepository, chatCreator, events);
	}

	@Test
	void deveCriarConversaQuandoNaoExiste() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(userRepository.findById(2L)).thenReturn(Optional.of(maria));
		when(chatRepository.findByDirectKey("1:2"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(chat(10L, alexandre, maria)));

		CreateChatResult result = chatService.create("alexandre@email.com", new CreateChatRequest(2L));

		assertThat(result.created()).isTrue();
		assertThat(result.chat().participants())
				.extracting(ParticipantResponse::id)
				.containsExactly(1L, 2L);
		verify(chatCreator).createDirect(1L, 2L);
	}

	@Test
	void deveReaproveitarConversaExistente() {
		Chat existente = chat(10L, alexandre, maria);
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(userRepository.findById(2L)).thenReturn(Optional.of(maria));
		when(chatRepository.findByDirectKey("1:2")).thenReturn(Optional.of(existente));

		CreateChatResult result = chatService.create("alexandre@email.com", new CreateChatRequest(2L));

		assertThat(result.created()).isFalse();
		assertThat(result.chat().id()).isEqualTo(10L);
		verifyNoInteractions(chatCreator);
	}

	@Test
	void chaveDaConversaDeveSerIgualEmQualquerOrdem() {
		assertThat(Chat.directKeyOf(2L, 10L)).isEqualTo("2:10");
		assertThat(Chat.directKeyOf(10L, 2L)).isEqualTo("2:10");
		assertThat(new Chat(maria, alexandre).getDirectKey()).isEqualTo("1:2");
	}

	@Test
	void conversaCriadaSimultaneamenteDeveSerReaproveitada() {
		Chat criadaPorOutraRequisicao = chat(10L, maria, alexandre);
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(userRepository.findById(2L)).thenReturn(Optional.of(maria));
		when(chatRepository.findByDirectKey("1:2"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(criadaPorOutraRequisicao));
		doThrow(new DataIntegrityViolationException("uk_chats_direct_key"))
				.when(chatCreator).createDirect(1L, 2L);

		CreateChatResult result = chatService.create("alexandre@email.com", new CreateChatRequest(2L));

		assertThat(result.created()).isFalse();
		assertThat(result.chat().id()).isEqualTo(10L);
	}

	@Test
	void outraViolacaoDeIntegridadeDevePropagar() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(userRepository.findById(2L)).thenReturn(Optional.of(maria));
		when(chatRepository.findByDirectKey("1:2")).thenReturn(Optional.empty());
		doThrow(new DataIntegrityViolationException("fk_chat_participants_user"))
				.when(chatCreator).createDirect(1L, 2L);

		assertThatThrownBy(() -> chatService.create("alexandre@email.com", new CreateChatRequest(2L)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void naoDevePermitirConversaConsigoMesmo() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));

		assertThatThrownBy(() -> chatService.create("alexandre@email.com", new CreateChatRequest(1L)))
				.isInstanceOf(BadRequestException.class);

		verifyNoInteractions(chatCreator);
	}

	@Test
	void deveFalharQuandoParticipanteNaoExiste() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.create("alexandre@email.com", new CreateChatRequest(99L)))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("99");

		verifyNoInteractions(chatCreator);
	}

	@Test
	void deveListarAsConversasDoUsuarioAutenticado() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(chatRepository.findAllByParticipantId(1L))
				.thenReturn(List.of(chat(11L, alexandre, joao), chat(10L, alexandre, maria)));

		assertThat(chatService.findMyChats("alexandre@email.com"))
				.extracting(ChatResponse::id)
				.containsExactly(11L, 10L);
	}

	@Test
	void listagemDeveTrazerUltimaMensagemENaoLidasSemConsultarPorConversa() {
		Chat comMaria = chat(10L, alexandre, maria);
		Chat comJoao = chat(11L, alexandre, joao);
		Message ultima = new Message(comMaria, maria, "oi, tudo bem?");
		ReflectionTestUtils.setField(ultima, "id", 99L);
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(chatRepository.findAllByParticipantId(1L)).thenReturn(List.of(comJoao, comMaria));
		when(messageRepository.findLastMessagesOfChats(List.of(11L, 10L))).thenReturn(List.of(ultima));
		when(messageRepository.countUnreadByChat(List.of(11L, 10L), 1L)).thenReturn(List.of(naoLidas(10L, 3L)));

		List<ChatResponse> chats = chatService.findMyChats("alexandre@email.com");

		assertThat(chats.get(0).lastMessage()).isNull();
		assertThat(chats.get(0).unreadCount()).isZero();
		assertThat(chats.get(1).lastMessage().content()).isEqualTo("oi, tudo bem?");
		assertThat(chats.get(1).lastMessage().senderId()).isEqualTo(2L);
		assertThat(chats.get(1).unreadCount()).isEqualTo(3);
	}

	@Test
	void usuarioSemConversasNaoDeveConsultarMensagens() {
		when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(joao));
		when(chatRepository.findAllByParticipantId(3L)).thenReturn(List.of());

		assertThat(chatService.findMyChats("joao@email.com")).isEmpty();
		verify(messageRepository, never()).findLastMessagesOfChats(any());
	}

	@Test
	void participanteDeveConsultarAConversa() {
		when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(maria));
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(chat(10L, alexandre, maria)));

		ChatResponse response = chatService.findById(10L, "maria@email.com");

		assertThat(response.participants())
				.extracting(ParticipantResponse::email)
				.containsExactly("alexandre@email.com", "maria@email.com");
	}

	@Test
	void naoParticipanteNaoDeveConsultarAConversa() {
		when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(joao));
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(chat(10L, alexandre, maria)));

		assertThatThrownBy(() -> chatService.findById(10L, "joao@email.com"))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void conversaInexistenteDeveResultarEm404() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(alexandre));
		when(chatRepository.findWithParticipantsById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> chatService.findById(99L, "alexandre@email.com"))
				.isInstanceOf(NotFoundException.class);
	}

	private static MessageRepository.UnreadCount naoLidas(Long chatId, Long total) {
		return new MessageRepository.UnreadCount() {
			@Override
			public Long getChatId() {
				return chatId;
			}

			@Override
			public Long getTotal() {
				return total;
			}
		};
	}

	private static User usuario(Long id, String name, String email) {
		User user = new User(name, email, "hash");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static Chat chat(Long id, User first, User second) {
		Chat chat = new Chat(first, second);
		ReflectionTestUtils.setField(chat, "id", id);
		return chat;
	}

}
