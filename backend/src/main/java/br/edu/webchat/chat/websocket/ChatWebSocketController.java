package br.edu.webchat.chat.websocket;

import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/** Os erros destes destinos sao tratados em {@link br.edu.webchat.shared.exception.WebSocketExceptionHandler}. */
@Controller
public class ChatWebSocketController {

	private final MessageService messageService;

	public ChatWebSocketController(MessageService messageService) {
		this.messageService = messageService;
	}

	@MessageMapping("/chats/{chatId}/messages")
	public void send(@DestinationVariable Long chatId, @Valid @Payload SendMessageRequest request, Principal principal) {
		messageService.send(chatId, principal.getName(), request);
	}

}
