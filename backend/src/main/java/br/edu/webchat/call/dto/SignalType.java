package br.edu.webchat.call.dto;

/** Mensagens WebRTC repassadas entre os navegadores: descricoes SDP e candidatos ICE. */
public enum SignalType {
	OFFER,
	ANSWER,
	ICE
}
