package br.edu.webchat.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Estrutura unica de resposta de erro da API.
 * O campo {@code fields} so aparece no JSON em erros de validacao.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		Map<String, String> fields
) {

	public static ApiError of(int status, String error, String message, String path) {
		return new ApiError(Instant.now(), status, error, message, path, null);
	}

	public static ApiError ofValidation(int status, String error, String message, String path,
			Map<String, String> fields) {
		return new ApiError(Instant.now(), status, error, message, path, fields);
	}

}
