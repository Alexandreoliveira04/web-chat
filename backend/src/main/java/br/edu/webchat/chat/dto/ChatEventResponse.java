package br.edu.webchat.chat.dto;

public record ChatEventResponse(
		ChatEventType event,
		Long chatId
) {

	public enum ChatEventType {

		CREATED,
		UPDATED,
		REMOVED

	}

}
