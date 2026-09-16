package br.edu.webchat.chat.dto;

import br.edu.webchat.chat.entity.Chat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameChatRequest(

		@NotBlank
		@Size(max = Chat.MAX_NAME_LENGTH)
		String name

) {
}
