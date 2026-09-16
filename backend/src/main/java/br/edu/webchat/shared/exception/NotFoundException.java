package br.edu.webchat.shared.exception;

/** Recurso solicitado nao existe. Resulta em 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
