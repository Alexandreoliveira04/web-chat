package br.edu.webchat.call.service;

import br.edu.webchat.call.dto.CallEndReason;
import br.edu.webchat.call.dto.CallEventResponse;
import br.edu.webchat.call.dto.CallEventType;
import br.edu.webchat.call.dto.CallSignalRequest;
import br.edu.webchat.call.dto.CallSignalResponse;
import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.ParticipantResponse;
import br.edu.webchat.chat.entity.ChatType;
import br.edu.webchat.chat.service.ChatService;
import br.edu.webchat.chat.websocket.WebSocketConfig;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.UserStatus;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Controla as chamadas de voz individuais. O estado fica em memoria: uma chamada so
 * existe enquanto as duas pontas estao conectadas, entao nao ha nada a persistir.
 *
 * <p>Cada usuario participa de no maximo uma chamada. A chamada fica presa a sessao
 * STOMP (aba) que ligou e a que atendeu: os sinais WebRTC so sao aceitos dessas
 * sessoes, e a queda de qualquer uma delas encerra a chamada.
 */
@Service
public class CallService {

	private final ChatService chatService;
	private final SimpMessagingTemplate messaging;
	private final Map<String, Call> callsByEmail = new HashMap<>();

	public CallService(ChatService chatService, SimpMessagingTemplate messaging) {
		this.chatService = chatService;
		this.messaging = messaging;
	}

	public synchronized void start(Long chatId, String email, String sessionId) {
		ChatResponse chat = chatService.findById(chatId, email);
		if (chat.type() != ChatType.DIRECT) {
			throw new BadRequestException("Chamadas de voz so estao disponiveis em conversas individuais");
		}

		ParticipantResponse me = participant(chat, email, true);
		ParticipantResponse other = participant(chat, email, false);
		if (callsByEmail.containsKey(key(me.email()))) {
			throw new ConflictException("Voce ja esta em uma chamada");
		}

		Call call = new Call(chatId, new Party(me.id(), me.email()), new Party(other.id(), other.email()));
		call.caller.sessionId = sessionId;

		if (other.status() != UserStatus.ONLINE) {
			sendEvent(me.email(), call, CallEventType.ENDED, CallEndReason.UNAVAILABLE);
			return;
		}
		if (callsByEmail.containsKey(key(other.email()))) {
			sendEvent(me.email(), call, CallEventType.ENDED, CallEndReason.BUSY);
			return;
		}

		callsByEmail.put(key(me.email()), call);
		callsByEmail.put(key(other.email()), call);
		broadcast(call, CallEventType.RINGING, null);
	}

	public synchronized void accept(Long chatId, String email, String sessionId) {
		Call call = findCall(chatId, email);
		if (!call.callee.is(email) || call.active) {
			throw new BadRequestException("Nao ha chamada aguardando resposta nesta conversa");
		}

		call.active = true;
		call.callee.sessionId = sessionId;
		broadcast(call, CallEventType.ACCEPTED, null);
	}

	public synchronized void end(Long chatId, String email) {
		Call call = findCall(chatId, email);

		CallEndReason reason;
		if (call.active) {
			reason = CallEndReason.HUNG_UP;
		}
		else {
			reason = call.caller.is(email) ? CallEndReason.CANCELED : CallEndReason.DECLINED;
		}

		finish(call, reason);
	}

	public synchronized void signal(Long chatId, String email, String sessionId, CallSignalRequest request) {
		Call call = findCall(chatId, email);
		Party from = call.caller.is(email) ? call.caller : call.callee;
		if (!call.active || !Objects.equals(from.sessionId, sessionId)) {
			throw new BadRequestException("Esta aba nao participa da chamada");
		}

		Party to = from == call.caller ? call.callee : call.caller;
		messaging.convertAndSendToUser(to.email, WebSocketConfig.CALL_SIGNAL_QUEUE,
				new CallSignalResponse(chatId, from.id, request.type(), request.data()));
	}

	@EventListener
	public synchronized void onDisconnected(SessionDisconnectEvent event) {
		String sessionId = event.getSessionId();
		callsByEmail.values().stream()
				.filter(call -> call.caller.hasSession(sessionId) || call.callee.hasSession(sessionId))
				.findFirst()
				.ifPresent(call -> finish(call, CallEndReason.DISCONNECTED));
	}

	private Call findCall(Long chatId, String email) {
		Call call = callsByEmail.get(key(email));
		if (call == null || !call.chatId.equals(chatId)) {
			throw new NotFoundException("Nenhuma chamada em andamento nesta conversa");
		}
		return call;
	}

	private void finish(Call call, CallEndReason reason) {
		callsByEmail.remove(key(call.caller.email));
		callsByEmail.remove(key(call.callee.email));
		broadcast(call, CallEventType.ENDED, reason);
	}

	private void broadcast(Call call, CallEventType type, CallEndReason reason) {
		for (Party party : List.of(call.caller, call.callee)) {
			sendEvent(party.email, call, type, reason);
		}
	}

	private void sendEvent(String email, Call call, CallEventType type, CallEndReason reason) {
		messaging.convertAndSendToUser(email, WebSocketConfig.CALLS_QUEUE,
				new CallEventResponse(type, call.chatId, call.caller.id, call.callee.id, reason));
	}

	private static String key(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

	private static ParticipantResponse participant(ChatResponse chat, String email, boolean self) {
		return chat.participants().stream()
				.filter(participant -> participant.email().equalsIgnoreCase(email) == self)
				.findFirst()
				.orElseThrow(() -> new BadRequestException("A outra pessoa nao participa mais desta conversa"));
	}

	private static final class Call {

		private final Long chatId;
		private final Party caller;
		private final Party callee;
		private boolean active;

		private Call(Long chatId, Party caller, Party callee) {
			this.chatId = chatId;
			this.caller = caller;
			this.callee = callee;
		}

	}

	private static final class Party {

		private final Long id;
		private final String email;
		private String sessionId;

		private Party(Long id, String email) {
			this.id = id;
			this.email = email;
		}

		private boolean is(String email) {
			return this.email.equalsIgnoreCase(email);
		}

		private boolean hasSession(String sessionId) {
			return sessionId != null && sessionId.equals(this.sessionId);
		}

	}

}
