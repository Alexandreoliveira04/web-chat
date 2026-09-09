package br.edu.webchat.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento global de excecoes. Toda resposta de erro da API usa {@link ApiError}.
 *
 * <p>Estende {@code ResponseEntityExceptionHandler} para que as excecoes proprias do
 * Spring MVC (rota inexistente, metodo nao suportado, corpo malformado, ...) mantenham
 * o status HTTP correto em vez de cairem no tratamento generico de erro 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Erros de validacao de entrada (Bean Validation) -> 400 com o detalhe por campo. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return ResponseEntity.status(status).body(ApiError.ofValidation(
				status.value(),
				reasonOf(status),
				"Dados invalidos",
				pathOf(request),
				fields));
	}

	/** Converte as demais excecoes padrao do Spring MVC para o formato {@link ApiError}. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.status(status).body(ApiError.of(
				status.value(),
				reasonOf(status),
				ex.getMessage(),
				pathOf(request)));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, WebRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
				HttpStatus.NOT_FOUND.value(),
				HttpStatus.NOT_FOUND.getReasonPhrase(),
				ex.getMessage(),
				pathOf(request)));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ConflictException ex, WebRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
				HttpStatus.CONFLICT.value(),
				HttpStatus.CONFLICT.getReasonPhrase(),
				ex.getMessage(),
				pathOf(request)));
	}

	/** Rede de seguranca: nada alem daqui deve vazar detalhe interno para o cliente. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
		log.error("Erro inesperado em {}", pathOf(request), ex);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
				"Erro interno do servidor",
				pathOf(request)));
	}

	private static String pathOf(WebRequest request) {
		return request instanceof ServletWebRequest servletRequest
				? servletRequest.getRequest().getRequestURI()
				: request.getDescription(false);
	}

	private static String reasonOf(HttpStatusCode status) {
		HttpStatus resolved = HttpStatus.resolve(status.value());
		return resolved != null ? resolved.getReasonPhrase() : "Error";
	}

}
