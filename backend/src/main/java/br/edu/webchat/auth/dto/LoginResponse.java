package br.edu.webchat.auth.dto;

public record LoginResponse(
		String token,
		String tokenType,
		long expiresIn
) {

	private static final String BEARER = "Bearer";

	public static LoginResponse bearer(String token, long expiresInSeconds) {
		return new LoginResponse(token, BEARER, expiresInSeconds);
	}

}
