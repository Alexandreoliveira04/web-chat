package br.edu.webchat.chat.controller;

import br.edu.webchat.chat.dto.EditMessageRequest;
import br.edu.webchat.chat.dto.MarkAsReadResponse;
import br.edu.webchat.chat.dto.MessageHistoryResponse;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chats/{chatId}/messages")
public class MessageController {

	private final MessageService messageService;

	public MessageController(MessageService messageService) {
		this.messageService = messageService;
	}

	@GetMapping
	public MessageHistoryResponse findHistory(@PathVariable Long chatId,
			@RequestParam(required = false) @Positive Long before,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
			Authentication authentication) {
		return messageService.findHistory(chatId, authentication.getName(), before, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse send(@PathVariable Long chatId, @Valid @RequestBody SendMessageRequest request,
			Authentication authentication) {
		return messageService.send(chatId, authentication.getName(), request);
	}

	@PatchMapping("/{messageId}")
	public MessageResponse edit(@PathVariable Long chatId, @PathVariable Long messageId,
			@Valid @RequestBody EditMessageRequest request, Authentication authentication) {
		return messageService.edit(chatId, messageId, authentication.getName(), request);
	}

	@DeleteMapping("/{messageId}")
	public MessageResponse delete(@PathVariable Long chatId, @PathVariable Long messageId,
			Authentication authentication) {
		return messageService.delete(chatId, messageId, authentication.getName());
	}

	@PatchMapping("/read")
	public MarkAsReadResponse markAsRead(@PathVariable Long chatId, Authentication authentication) {
		return messageService.markAsRead(chatId, authentication.getName());
	}

}
