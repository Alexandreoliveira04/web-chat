package br.edu.webchat.call.service;

import br.edu.webchat.call.dto.CallEndReason;
import br.edu.webchat.call.dto.CallEventResponse;
import br.edu.webchat.call.dto.CallEventType;
import br.edu.webchat.call.dto.CallSignalRequest;
import br.edu.webchat.call.dto.CallSignalResponse;
import br.edu.webchat.call.dto.SignalType;
import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.ParticipantResponse;
import br.edu.webchat.chat.entity.ChatType;
import br.edu.webchat.chat.service.ChatService;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.UserStatus;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {

	private static final Long CHAT_ID = 10L;
	private static final Long GROUP_ID = 20L;
	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String RENE = "rene@email.com";
	private static final String SAVIO = "savio@email.com";
	private static final String CALLS = "/queue/calls";
	private static final String SIGNALS = "/queue/call-signal";

	@Mock
	private ChatService chatService;

	@Mock
	private SimpMessagingTemplate messaging;

	private CallService callService;

	@BeforeEach
	void setUp() {
		callService = new CallService(chatService, messaging);
	}

	@Test
	void ligarFazTocarParaOsDois() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);

		callService.start(CHAT_ID, ALEXANDRE, "s1");

		verify(messaging).convertAndSendToUser(ALEXANDRE, CALLS, event(CallEventType.RINGING, null));
		verify(messaging).convertAndSendToUser(RENE, CALLS, event(CallEventType.RINGING, null));
	}

	@Test
	void ligarParaQuemEstaOfflineAvisaSoQuemLigou() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.OFFLINE);

		callService.start(CHAT_ID, ALEXANDRE, "s1");

		verify(messaging).convertAndSendToUser(ALEXANDRE, CALLS, event(CallEventType.ENDED, CallEndReason.UNAVAILABLE));
		verify(messaging, never()).convertAndSendToUser(RENE, CALLS, event(CallEventType.RINGING, null));
	}

	@Test
	void ligarParaQuemJaEstaEmChamadaDaOcupado() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);
		directChat(30L, SAVIO, RENE, UserStatus.ONLINE);
		callService.start(CHAT_ID, ALEXANDRE, "s1");

		callService.start(30L, SAVIO, "s3");

		verify(messaging).convertAndSendToUser(SAVIO, CALLS,
				new CallEventResponse(CallEventType.ENDED, 30L, 3L, 2L, CallEndReason.BUSY));
	}

	@Test
	void naoDaParaLigarDuasVezesNemEmGrupo() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);
		callService.start(CHAT_ID, ALEXANDRE, "s1");

		assertThatThrownBy(() -> callService.start(CHAT_ID, ALEXANDRE, "s1"))
				.isInstanceOf(ConflictException.class);

		when(chatService.findById(GROUP_ID, ALEXANDRE)).thenReturn(chat(GROUP_ID, ChatType.GROUP, UserStatus.ONLINE,
				ALEXANDRE, RENE));
		assertThatThrownBy(() -> callService.start(GROUP_ID, ALEXANDRE, "s1"))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void atenderAvisaOsDoisESoQuemRecebeuPodeAtender() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);
		callService.start(CHAT_ID, ALEXANDRE, "s1");

		assertThatThrownBy(() -> callService.accept(CHAT_ID, ALEXANDRE, "s1"))
				.isInstanceOf(BadRequestException.class);

		callService.accept(CHAT_ID, RENE, "s2");

		verify(messaging).convertAndSendToUser(ALEXANDRE, CALLS, event(CallEventType.ACCEPTED, null));
		verify(messaging).convertAndSendToUser(RENE, CALLS, event(CallEventType.ACCEPTED, null));
	}

	@Test
	void motivoDoEncerramentoDependeDeQuemEQuando() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);

		callService.start(CHAT_ID, ALEXANDRE, "s1");
		callService.end(CHAT_ID, ALEXANDRE);
		verify(messaging).convertAndSendToUser(RENE, CALLS, event(CallEventType.ENDED, CallEndReason.CANCELED));

		callService.start(CHAT_ID, ALEXANDRE, "s1");
		callService.end(CHAT_ID, RENE);
		verify(messaging).convertAndSendToUser(ALEXANDRE, CALLS, event(CallEventType.ENDED, CallEndReason.DECLINED));

		callService.start(CHAT_ID, ALEXANDRE, "s1");
		callService.accept(CHAT_ID, RENE, "s2");
		callService.end(CHAT_ID, RENE);
		verify(messaging).convertAndSendToUser(ALEXANDRE, CALLS, event(CallEventType.ENDED, CallEndReason.HUNG_UP));

		assertThatThrownBy(() -> callService.end(CHAT_ID, ALEXANDRE))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void sinalERepassadoParaAOutraPontaSoPelasAbasDaChamada() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);
		CallSignalRequest offer = new CallSignalRequest(SignalType.OFFER, JsonNodeFactory.instance.textNode("sdp"));
		callService.start(CHAT_ID, ALEXANDRE, "s1");

		assertThatThrownBy(() -> callService.signal(CHAT_ID, ALEXANDRE, "s1", offer))
				.isInstanceOf(BadRequestException.class);

		callService.accept(CHAT_ID, RENE, "s2");
		callService.signal(CHAT_ID, ALEXANDRE, "s1", offer);

		verify(messaging).convertAndSendToUser(RENE, SIGNALS, new CallSignalResponse(CHAT_ID, 1L, SignalType.OFFER, offer.data()));
		assertThatThrownBy(() -> callService.signal(CHAT_ID, RENE, "outra-aba", offer))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void quedaDaAbaDaChamadaEncerraEAbaDesconhecidaNao() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);
		callService.start(CHAT_ID, ALEXANDRE, "s1");

		callService.onDisconnected(disconnected("outra"));

		verify(messaging, never()).convertAndSendToUser(RENE, CALLS, event(CallEventType.ENDED, CallEndReason.DISCONNECTED));
		assertThatThrownBy(() -> callService.start(CHAT_ID, ALEXANDRE, "s1"))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void quedaDeQuemLigouEncerraAChamadaComoDesconectada() {
		directChat(CHAT_ID, ALEXANDRE, RENE, UserStatus.ONLINE);
		callService.start(CHAT_ID, ALEXANDRE, "s1");

		callService.onDisconnected(disconnected("s1"));

		verify(messaging).convertAndSendToUser(ALEXANDRE, CALLS, event(CallEventType.ENDED, CallEndReason.DISCONNECTED));
		verify(messaging).convertAndSendToUser(RENE, CALLS, event(CallEventType.ENDED, CallEndReason.DISCONNECTED));
		assertThatThrownBy(() -> callService.end(CHAT_ID, RENE))
				.isInstanceOf(NotFoundException.class);
	}

	private void directChat(Long chatId, String caller, String callee, UserStatus calleeStatus) {
		when(chatService.findById(chatId, caller)).thenReturn(chat(chatId, ChatType.DIRECT, calleeStatus, caller, callee));
	}

	private static ChatResponse chat(Long chatId, ChatType type, UserStatus othersStatus, String me, String... others) {
		List<ParticipantResponse> participants = new ArrayList<>();
		participants.add(participant(me, UserStatus.ONLINE));
		for (String other : others) {
			participants.add(participant(other, othersStatus));
		}
		return new ChatResponse(chatId, type, null, null, null, participants, null, 0, null, Instant.now(), Instant.now());
	}

	private static ParticipantResponse participant(String email, UserStatus status) {
		long id = switch (email) {
			case ALEXANDRE -> 1L;
			case RENE -> 2L;
			default -> 3L;
		};
		return new ParticipantResponse(id, email, email, status, null);
	}

	private static CallEventResponse event(CallEventType type, CallEndReason reason) {
		return new CallEventResponse(type, CHAT_ID, 1L, 2L, reason);
	}

	private static SessionDisconnectEvent disconnected(String sessionId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		accessor.setSessionId(sessionId);
		return new SessionDisconnectEvent(new Object(),
				MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), sessionId, CloseStatus.NORMAL);
	}

}
