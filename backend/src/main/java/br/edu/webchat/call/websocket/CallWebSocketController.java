package br.edu.webchat.call.websocket;

import br.edu.webchat.call.dto.CallSignalRequest;
import br.edu.webchat.call.service.CallService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Sinalizacao das chamadas de voz. O audio nao passa pelo servidor: ele so apresenta
 * os dois navegadores um ao outro, que depois conversam direto via WebRTC.
 */
@Controller
public class CallWebSocketController {

	private final CallService callService;

	public CallWebSocketController(CallService callService) {
		this.callService = callService;
	}

	@MessageMapping("/calls/{chatId}/start")
	public void start(@DestinationVariable Long chatId, Principal principal, SimpMessageHeaderAccessor headers) {
		callService.start(chatId, principal.getName(), headers.getSessionId());
	}

	@MessageMapping("/calls/{chatId}/accept")
	public void accept(@DestinationVariable Long chatId, Principal principal, SimpMessageHeaderAccessor headers) {
		callService.accept(chatId, principal.getName(), headers.getSessionId());
	}

	@MessageMapping("/calls/{chatId}/end")
	public void end(@DestinationVariable Long chatId, Principal principal) {
		callService.end(chatId, principal.getName());
	}

	@MessageMapping("/calls/{chatId}/signal")
	public void signal(@DestinationVariable Long chatId, @Valid @Payload CallSignalRequest request,
			Principal principal, SimpMessageHeaderAccessor headers) {
		callService.signal(chatId, principal.getName(), headers.getSessionId(), request);
	}

}
