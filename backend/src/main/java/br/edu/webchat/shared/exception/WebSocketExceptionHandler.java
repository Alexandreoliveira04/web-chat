package br.edu.webchat.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento de erros dos destinos STOMP (@MessageMapping) de todos os modulos.
 * O erro volta somente para a sessao que enviou o frame, na fila de erros.
 */
@ControllerAdvice
public class WebSocketExceptionHandler {

	/** Fila de erros de cada usuario; o modulo chat a libera na assinatura. */
	public static final String ERRORS_QUEUE = "/queue/errors";

	private static final Logger log = LoggerFactory.getLogger(WebSocketExceptionHandler.class);

	@MessageExceptionHandler(MethodArgumentNotValidException.class)
	@SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
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
	@SendToUser(destinations = ERRORS_QUEUE, broadcast = false)
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
		if (ex instanceof BadRequestException || ex instanceof MessageConversionException) {
			return HttpStatus.BAD_REQUEST;
		}
		if (ex instanceof ForbiddenException) {
			return HttpStatus.FORBIDDEN;
		}
		if (ex instanceof NotFoundException) {
			return HttpStatus.NOT_FOUND;
		}
		if (ex instanceof ConflictException) {
			return HttpStatus.CONFLICT;
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

}
