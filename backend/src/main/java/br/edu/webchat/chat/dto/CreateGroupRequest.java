package br.edu.webchat.chat.dto;

import br.edu.webchat.chat.entity.Chat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateGroupRequest(

		@NotBlank
		@Size(max = Chat.MAX_NAME_LENGTH)
		String name,

		@NotEmpty
		@Size(max = Chat.MAX_PARTICIPANTS - 1)
		Set<@NotNull @Positive Long> participantIds

) {
}
