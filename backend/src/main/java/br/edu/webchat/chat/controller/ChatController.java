package br.edu.webchat.chat.controller;

import br.edu.webchat.chat.dto.AddParticipantRequest;
import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.dto.CreateGroupRequest;
import br.edu.webchat.chat.dto.RenameChatRequest;
import br.edu.webchat.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
	public List<ChatResponse> findMyChats(Authentication authentication, @org.springframework.web.bind.annotation.RequestParam(required = false) String search) {
		return chatService.findMyChats(authentication.getName(), search);
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

	@PostMapping("/groups")
	public ResponseEntity<ChatResponse> createGroup(@Valid @RequestBody CreateGroupRequest request,
			Authentication authentication, UriComponentsBuilder uriBuilder) {
		ChatResponse group = chatService.createGroup(authentication.getName(), request);

		return ResponseEntity
				.created(uriBuilder.path("/api/v1/chats/{id}").buildAndExpand(group.id()).toUri())
				.body(group);
	}

	@GetMapping("/{chatId}")
	public ChatResponse findById(@PathVariable Long chatId, Authentication authentication) {
		return chatService.findById(chatId, authentication.getName());
	}

	@PatchMapping("/{chatId}")
	public ChatResponse rename(@PathVariable Long chatId, @Valid @RequestBody RenameChatRequest request,
			Authentication authentication) {
		return chatService.rename(chatId, authentication.getName(), request);
	}

	@PostMapping("/{chatId}/participants")
	public ChatResponse addParticipant(@PathVariable Long chatId, @Valid @RequestBody AddParticipantRequest request,
			Authentication authentication) {
		return chatService.addParticipant(chatId, authentication.getName(), request);
	}

	@DeleteMapping("/{chatId}/participants/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@PathVariable Long chatId, Authentication authentication) {
		chatService.leave(chatId, authentication.getName());
	}

	@DeleteMapping("/{chatId}/participants/{participantId}")
	public ChatResponse removeParticipant(@PathVariable Long chatId, @PathVariable Long participantId,
			Authentication authentication) {
		return chatService.removeParticipant(chatId, authentication.getName(), participantId);
	}

}
