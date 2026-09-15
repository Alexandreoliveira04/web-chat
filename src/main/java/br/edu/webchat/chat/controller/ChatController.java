package br.edu.webchat.chat.controller;

import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@GetMapping
	public List<ChatResponse> findMyChats(Authentication authentication) {
		return chatService.findMyChats(authentication.getName());
	}

	@PostMapping
	public ResponseEntity<ChatResponse> create(@Valid @RequestBody CreateChatRequest request,
			Authentication authentication, UriComponentsBuilder uriBuilder) {
		CreateChatResult result = chatService.create(authentication.getName(), request);

		if (!result.created()) {
			return ResponseEntity.ok(result.chat());
		}

		return ResponseEntity
				.created(uriBuilder.path("/api/v1/chats/{id}").buildAndExpand(result.chat().id()).toUri())
				.body(result.chat());
	}

	@GetMapping("/{chatId}")
	public ChatResponse findById(@PathVariable Long chatId, Authentication authentication) {
		return chatService.findById(chatId, authentication.getName());
	}

}
