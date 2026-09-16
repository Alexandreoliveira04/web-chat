package br.edu.webchat.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddParticipantRequest(

		@NotNull
		@Positive
		Long userId

) {
}
