package br.edu.webchat.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateChatRequest(

		@NotNull
		@Positive
		Long participantId

) {
}
