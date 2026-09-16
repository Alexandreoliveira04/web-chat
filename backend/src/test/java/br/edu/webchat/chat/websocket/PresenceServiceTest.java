package br.edu.webchat.chat.websocket;

import br.edu.webchat.chat.dto.PresenceResponse;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

	private static final String EMAIL = "alexandre@email.com";
	private static final Principal ALEXANDRE = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

	@Mock
	private UserService userService;

	@Mock
	private SimpMessagingTemplate messaging;

	private PresenceService presenceService;

	@BeforeEach
	void setUp() {
		presenceService = new PresenceService(userService, messaging);
	}

	@Test
	void primeiraSessaoDeixaOUsuarioOnline() {
		when(userService.changeStatus(EMAIL, UserStatus.ONLINE)).thenReturn(1L);

		presenceService.onConnected(connected("s1", ALEXANDRE));

		verify(messaging).convertAndSend("/topic/presence", new PresenceResponse(1L, UserStatus.ONLINE));
	}

	@Test
	void segundaAbaNaoRepeteOEventoEFecharUmaDelasNaoDeixaOffline() {
		when(userService.changeStatus(EMAIL, UserStatus.ONLINE)).thenReturn(1L);
		when(userService.changeStatus(EMAIL, UserStatus.OFFLINE)).thenReturn(1L);

		presenceService.onConnected(connected("s1", ALEXANDRE));
		presenceService.onConnected(connected("s2", ALEXANDRE));
		presenceService.onDisconnected(disconnected("s1", ALEXANDRE));

		verify(userService, times(1)).changeStatus(EMAIL, UserStatus.ONLINE);
		verify(userService, never()).changeStatus(EMAIL, UserStatus.OFFLINE);

		presenceService.onDisconnected(disconnected("s2", ALEXANDRE));

		verify(messaging).convertAndSend("/topic/presence", new PresenceResponse(1L, UserStatus.OFFLINE));
	}

	@Test
	void desconexaoRepetidaOuDeSessaoDesconhecidaNaoMudaStatus() {
		when(userService.changeStatus(EMAIL, UserStatus.ONLINE)).thenReturn(1L);
		when(userService.changeStatus(EMAIL, UserStatus.OFFLINE)).thenReturn(1L);

		presenceService.onDisconnected(disconnected("desconhecida", ALEXANDRE));
		presenceService.onConnected(connected("s1", ALEXANDRE));
		presenceService.onDisconnected(disconnected("s1", ALEXANDRE));
		presenceService.onDisconnected(disconnected("s1", ALEXANDRE));

		verify(userService, times(1)).changeStatus(EMAIL, UserStatus.OFFLINE);
	}

	@Test
	void sessaoSemUsuarioAutenticadoEIgnorada() {
		presenceService.onConnected(connected("s1", null));
		presenceService.onDisconnected(disconnected("s1", null));

		verifyNoInteractions(userService);
		verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void inicializacaoMarcaTodosComoOffline() {
		presenceService.resetStatuses();

		verify(userService).markAllOffline();
	}

	private static SessionConnectedEvent connected(String sessionId, Principal user) {
		return new SessionConnectedEvent(new Object(), message(StompCommand.CONNECTED, sessionId), user);
	}

	private static SessionDisconnectEvent disconnected(String sessionId, Principal user) {
		return new SessionDisconnectEvent(new Object(), message(StompCommand.DISCONNECT, sessionId), sessionId,
				CloseStatus.NORMAL, user);
	}

	private static Message<byte[]> message(StompCommand command, String sessionId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setSessionId(sessionId);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

}
