package br.edu.webchat.call.dto;

public enum CallEndReason {
	/** Quem ligou desistiu antes de a chamada ser atendida. */
	CANCELED,
	/** Quem recebeu a ligacao recusou. */
	DECLINED,
	/** Um dos dois desligou uma chamada em andamento. */
	HUNG_UP,
	/** A outra pessoa ja esta em outra chamada. */
	BUSY,
	/** A outra pessoa esta offline. */
	UNAVAILABLE,
	/** A conexao de um dos dois caiu. */
	DISCONNECTED
}
