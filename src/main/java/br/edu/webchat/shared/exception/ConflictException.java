package br.edu.webchat.shared.exception;

/** Operacao conflita com o estado atual dos dados. Resulta em 409. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
