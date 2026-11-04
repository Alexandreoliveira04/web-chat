package br.edu.webchat.chat.websocket;

import br.edu.webchat.chat.service.ChatChangedEvent;
import br.edu.webchat.chat.service.MessageSentEvent;
import br.edu.webchat.chat.service.MessageUpdatedEvent;
import br.edu.webchat.chat.service.MessagesReadEvent;
import br.edu.webchat.user.service.ProfileChangedEvent;
import br.edu.webchat.user.service.UserCreatedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatEventBroadcaster {

	private final SimpMessagingTemplate messaging;

	public ChatEventBroadcaster(SimpMessagingTemplate messaging) {
		this.messaging = messaging;
	}

	@TransactionalEventListener
	public void onMessageSent(MessageSentEvent event) {
		event.recipientEmails().forEach(email ->
				messaging.convertAndSendToUser(email, WebSocketConfig.MESSAGES_QUEUE, event.message()));
	}

	@TransactionalEventListener
	public void onMessageUpdated(MessageUpdatedEvent event) {
		event.recipientEmails().forEach(email ->
				messaging.convertAndSendToUser(email, WebSocketConfig.MESSAGE_UPDATES_QUEUE, event.message()));
	}

	@TransactionalEventListener
	public void onChatChanged(ChatChangedEvent event) {
		event.recipientEmails().forEach(email ->
				messaging.convertAndSendToUser(email, WebSocketConfig.CHATS_QUEUE, event.event()));
	}

	@TransactionalEventListener
	public void onMessagesRead(MessagesReadEvent event) {
		event.recipientEmails().forEach(email ->
				messaging.convertAndSendToUser(email, WebSocketConfig.READ_QUEUE, event.receipt()));
	}

	@TransactionalEventListener
	public void onProfileChanged(ProfileChangedEvent event) {
		messaging.convertAndSend(WebSocketConfig.PROFILES_TOPIC, event.profile());
	}

	@TransactionalEventListener
	public void onUserCreated(UserCreatedEvent event) {
		messaging.convertAndSend(WebSocketConfig.USERS_TOPIC, event.user());
	}

}
