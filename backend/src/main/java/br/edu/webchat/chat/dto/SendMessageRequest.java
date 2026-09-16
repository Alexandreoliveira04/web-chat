package br.edu.webchat.chat.dto;

import br.edu.webchat.chat.entity.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(

		@NotBlank
		@Size(max = Message.MAX_CONTENT_LENGTH)
		String content

) {
}
