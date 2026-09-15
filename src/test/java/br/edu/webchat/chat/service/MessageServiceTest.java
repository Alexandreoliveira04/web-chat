package br.edu.webchat.chat.service;

import br.edu.webchat.chat.dto.MessageHistoryResponse;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

	@Mock
	private ChatRepository chatRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ApplicationEventPublisher events;

	private MessageService messageService;

	private final User alexandre = usuario(1L, "Alexandre", "alexandre@email.com");
	private final User maria = usuario(2L, "Maria", "maria@email.com");
	private final User joao = usuario(3L, "Joao", "joao@email.com");

	private Chat conversa;

	@BeforeEach
	void setUp() {
		ChatService chatService = new ChatService(chatRepository, messageRepository, userRepository);
		messageService = new MessageService(chatService, messageRepository, events);

		conversa = new Chat(alexandre, maria);
		ReflectionTestUtils.setField(conversa, "id", 10L);
		ReflectionTestUtils.setField(conversa, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
	}

	// ---------- envio ----------

	@Test
	void remetenteDeveSerOUsuarioAutenticadoEConteudoSemEspacosNasPontas() {
		logado(alexandre);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> comId(invocation.getArgument(0), 50L));

		MessageResponse response = messageService.send(10L, "alexandre@email.com", new SendMessageRequest("  oi Maria  "));

		ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
		verify(messageRepository).save(saved.capture());
		assertThat(saved.getValue().getSender()).isSameAs(alexandre);
		assertThat(saved.getValue().getChat()).isSameAs(conversa);
		assertThat(response.content()).isEqualTo("oi Maria");
		assertThat(response.senderId()).isEqualTo(1L);
		assertThat(response.chatId()).isEqualTo(10L);
		assertThat(response.readAt()).isNull();
	}

	@Test
	void envioDevePublicarEventoParaOsDoisParticipantes() {
		logado(alexandre);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> comId(invocation.getArgument(0), 50L));

		MessageResponse response = messageService.send(10L, "alexandre@email.com", new SendMessageRequest("oi"));

		ArgumentCaptor<MessageSentEvent> event = ArgumentCaptor.forClass(MessageSentEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().message()).isEqualTo(response);
		assertThat(event.getValue().recipientEmails()).containsExactly("alexandre@email.com", "maria@email.com");
	}

	@Test
	void envioRecusadoNaoPublicaEvento() {
		logado(joao);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));

		assertThatThrownBy(() -> messageService.send(10L, "joao@email.com", new SendMessageRequest("oi")))
				.isInstanceOf(ForbiddenException.class);

		verifyNoInteractions(events);
	}

	@Test
	void envioDeveAtualizarAAtividadeDaConversa() {
		logado(alexandre);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

		messageService.send(10L, "alexandre@email.com", new SendMessageRequest("oi"));

		assertThat(conversa.getUpdatedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
	}

	@Test
	void naoParticipanteNaoDeveEnviarMensagem() {
		logado(joao);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));

		assertThatThrownBy(() -> messageService.send(10L, "joao@email.com", new SendMessageRequest("oi")))
				.isInstanceOf(ForbiddenException.class);

		verify(messageRepository, never()).save(any());
	}

	@Test
	void envioParaConversaInexistenteDeveResultarEm404() {
		logado(alexandre);
		when(chatRepository.findWithParticipantsById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> messageService.send(99L, "alexandre@email.com", new SendMessageRequest("oi")))
				.isInstanceOf(NotFoundException.class);
	}

	// ---------- historico ----------

	@Test
	void historicoDeveVirEmOrdemCronologicaComCursorQuandoHaMais() {
		logado(maria);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.findLatest(eq(10L), any(Pageable.class))).thenReturn(mensagensDecrescentes(10, 7));

		MessageHistoryResponse history = messageService.findHistory(10L, "maria@email.com", null, 3);

		assertThat(history.messages()).extracting(MessageResponse::id).containsExactly(8L, 9L, 10L);
		assertThat(history.hasMore()).isTrue();
		assertThat(history.nextBefore()).isEqualTo(8L);
	}

	@Test
	void historicoDeveBuscarUmaMensagemAMaisParaSaberSeHaMais() {
		logado(maria);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.findLatest(eq(10L), any(Pageable.class))).thenReturn(List.of());

		messageService.findHistory(10L, "maria@email.com", null, 3);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(messageRepository).findLatest(eq(10L), pageable.capture());
		assertThat(pageable.getValue().getPageSize()).isEqualTo(4);
		assertThat(pageable.getValue().getOffset()).isZero();
	}

	@Test
	void ultimaPaginaDoHistoricoNaoTemCursor() {
		logado(maria);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.findBefore(eq(10L), eq(8L), any(Pageable.class))).thenReturn(mensagensDecrescentes(7, 6));

		MessageHistoryResponse history = messageService.findHistory(10L, "maria@email.com", 8L, 3);

		assertThat(history.messages()).extracting(MessageResponse::id).containsExactly(6L, 7L);
		assertThat(history.hasMore()).isFalse();
		assertThat(history.nextBefore()).isNull();
		verify(messageRepository, never()).findLatest(anyLong(), any());
	}

	@Test
	void naoParticipanteNaoDeveLerOHistorico() {
		logado(joao);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));

		assertThatThrownBy(() -> messageService.findHistory(10L, "joao@email.com", null, 50))
				.isInstanceOf(ForbiddenException.class);

		verify(messageRepository, never()).findLatest(anyLong(), any());
	}

	// ---------- leitura ----------

	@Test
	void marcarComoLidaDeveAfetarSomenteMensagensDoOutroParticipante() {
		logado(maria);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.markAsRead(eq(10L), eq(2L), any(Instant.class))).thenReturn(3);

		assertThat(messageService.markAsRead(10L, "maria@email.com").markedAsRead()).isEqualTo(3);

		ArgumentCaptor<MessagesReadEvent> event = ArgumentCaptor.forClass(MessagesReadEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().receipt().chatId()).isEqualTo(10L);
		assertThat(event.getValue().receipt().readerId()).isEqualTo(2L);
		assertThat(event.getValue().receipt().markedAsRead()).isEqualTo(3);
		assertThat(event.getValue().receipt().readAt()).isNotNull();
		assertThat(event.getValue().recipientEmails()).containsExactly("alexandre@email.com", "maria@email.com");
	}

	@Test
	void marcarComoLidaSemNadaPendenteNaoPublicaEvento() {
		logado(maria);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));
		when(messageRepository.markAsRead(eq(10L), eq(2L), any(Instant.class))).thenReturn(0);

		assertThat(messageService.markAsRead(10L, "maria@email.com").markedAsRead()).isZero();

		verifyNoInteractions(events);
	}

	@Test
	void naoParticipanteNaoDeveMarcarComoLida() {
		logado(joao);
		when(chatRepository.findWithParticipantsById(10L)).thenReturn(Optional.of(conversa));

		assertThatThrownBy(() -> messageService.markAsRead(10L, "joao@email.com"))
				.isInstanceOf(ForbiddenException.class);

		verify(messageRepository, never()).markAsRead(anyLong(), anyLong(), any());
	}

	private void logado(User user) {
		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
	}

	private List<Message> mensagensDecrescentes(long from, long to) {
		return LongStream.iterate(from, id -> id >= to, id -> id - 1)
				.mapToObj(id -> comId(new Message(conversa, alexandre, "msg " + id), id))
				.toList();
	}

	private static Message comId(Message message, Long id) {
		ReflectionTestUtils.setField(message, "id", id);
		return message;
	}

	private static User usuario(Long id, String name, String email) {
		User user = new User(name, email, "hash");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
