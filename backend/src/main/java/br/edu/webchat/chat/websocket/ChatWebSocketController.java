package br.edu.webchat.chat.websocket;

import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.service.MessageService;
import br.edu.webchat.shared.exception.ApiError;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class ChatWebSocketController {

	private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

	private final MessageService messageService;

	public ChatWebSocketController(MessageService messageService) {
		this.messageService = messageService;
	}

	@MessageMapping("/chats/{chatId}/messages")
	public void send(@DestinationVariable Long chatId, @Valid @Payload SendMessageRequest request, Principal principal) {
		messageService.send(chatId, principal.getName(), request);
	}

	@MessageExceptionHandler(MethodArgumentNotValidException.class)
	@SendToUser(destinations = WebSocketConfig.ERRORS_QUEUE, broadcast = false)
	public ApiError handleValidation(MethodArgumentNotValidException ex,
			@Header(name = SimpMessageHeaderAccessor.DESTINATION_HEADER, required = false) String destination) {
		Map<String, String> fields = new LinkedHashMap<>();
		if (ex.getBindingResult() != null) {
			ex.getBindingResult().getFieldErrors()
					.forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
		}

		return ApiError.ofValidation(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
				"Dados invalidos", destination, fields);
	}

	@MessageExceptionHandler
	@SendToUser(destinations = WebSocketConfig.ERRORS_QUEUE, broadcast = false)
	public ApiError handleException(Exception ex,
			@Header(name = SimpMessageHeaderAccessor.DESTINATION_HEADER, required = false) String destination) {
		HttpStatus status = statusOf(ex);

		if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
			log.error("Erro inesperado em {}", destination, ex);
			return ApiError.of(status.value(), status.getReasonPhrase(), "Erro interno do servidor", destination);
		}

		return ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(), destination);
	}

	private static HttpStatus statusOf(Exception ex) {
		if (ex instanceof BadRequestException) {
			return HttpStatus.BAD_REQUEST;
		}
		if (ex instanceof ForbiddenException) {
			return HttpStatus.FORBIDDEN;
		}
		if (ex instanceof NotFoundException) {
			return HttpStatus.NOT_FOUND;
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

}
