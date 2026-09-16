package br.edu.webchat.chat.websocket;

import br.edu.webchat.chat.dto.PresenceResponse;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class PresenceService {

	private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

	private final UserService userService;
	private final SimpMessagingTemplate messaging;
	private final Map<String, Set<String>> sessionsByUser = new HashMap<>();

	public PresenceService(UserService userService, SimpMessagingTemplate messaging) {
		this.userService = userService;
		this.messaging = messaging;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void resetStatuses() {
		int reset = userService.markAllOffline();
		if (reset > 0) {
			log.info("{} usuario(s) marcado(s) como OFFLINE na inicializacao", reset);
		}
	}

	@EventListener
	public synchronized void onConnected(SessionConnectedEvent event) {
		String email = emailOf(event);
		if (email == null) {
			return;
		}

		Set<String> sessions = sessionsByUser.computeIfAbsent(email, key -> new HashSet<>());
		String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
		if (sessions.add(sessionId) && sessions.size() == 1) {
			publish(email, UserStatus.ONLINE);
		}
	}

	@EventListener
	public synchronized void onDisconnected(SessionDisconnectEvent event) {
		String email = emailOf(event);
		if (email == null) {
			return;
		}

		Set<String> sessions = sessionsByUser.get(email);
		if (sessions == null || !sessions.remove(event.getSessionId())) {
			return;
		}

		if (sessions.isEmpty()) {
			sessionsByUser.remove(email);
			publish(email, UserStatus.OFFLINE);
		}
	}

	private void publish(String email, UserStatus status) {
		Long userId = userService.changeStatus(email, status);
		messaging.convertAndSend(WebSocketConfig.PRESENCE_TOPIC, new PresenceResponse(userId, status));
	}

	private static String emailOf(AbstractSubProtocolEvent event) {
		Principal user = event.getUser();
		return user == null ? null : user.getName();
	}

}
