package br.edu.webchat.game.websocket;

import br.edu.webchat.chat.websocket.WebSocketConfig;
import br.edu.webchat.game.service.GameChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega as mudancas de partida so depois do commit: uma jogada que falhar por conflito
 * de versao nunca chega a ser anunciada.
 */
@Component
public class GameEventBroadcaster {

	private final SimpMessagingTemplate messaging;

	public GameEventBroadcaster(SimpMessagingTemplate messaging) {
		this.messaging = messaging;
	}

	@TransactionalEventListener
	public void onGameChanged(GameChangedEvent event) {
		event.recipientEmails().forEach(email ->
				messaging.convertAndSendToUser(email, WebSocketConfig.GAMES_QUEUE, event.game()));
	}

}
