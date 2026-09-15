package br.edu.webchat.chat.controller;

import br.edu.webchat.chat.dto.MarkAsReadResponse;
import br.edu.webchat.chat.dto.MessageHistoryResponse;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.service.MessageService;
import br.edu.webchat.shared.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
// addFilters = false: estes testes verificam o controller, nao a seguranca.
// A cadeia de seguranca real e coberta por MessageIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerTest {

	private static final String EMAIL = "alexandre@email.com";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MessageService messageService;

	private static final Authentication LOGADO = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

	private static final MessageResponse MENSAGEM = new MessageResponse(
			50L, 10L, 1L, "oi Maria", Instant.parse("2026-09-14T22:00:00Z"), null);

	@Test
	void postDeveRetornar201ComAMensagem() throws Exception {
		when(messageService.send(eq(10L), eq(EMAIL), any(SendMessageRequest.class))).thenReturn(MENSAGEM);

		mockMvc.perform(enviar("{\"content\":\"oi Maria\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(50))
				.andExpect(jsonPath("$.chatId").value(10))
				.andExpect(jsonPath("$.senderId").value(1))
				.andExpect(jsonPath("$.content").value("oi Maria"))
				.andExpect(jsonPath("$.createdAt").value("2026-09-14T22:00:00Z"))
				.andExpect(jsonPath("$.readAt").isEmpty());
	}

	@Test
	void postComConteudoEmBrancoDeveRetornar400() throws Exception {
		mockMvc.perform(enviar("{\"content\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.content").exists());

		verifyNoInteractions(messageService);
	}

	@Test
	void postComConteudoMaiorQueOLimiteDeveRetornar400() throws Exception {
		mockMvc.perform(enviar("{\"content\":\"" + "a".repeat(2001) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.content").exists());
	}

	@Test
	void postSemParticiparDeveRetornar403() throws Exception {
		when(messageService.send(eq(10L), eq(EMAIL), any(SendMessageRequest.class)))
				.thenThrow(new ForbiddenException("Voce nao participa desta conversa"));

		mockMvc.perform(enviar("{\"content\":\"oi\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void getDeveUsarTamanhoPadrao50SemCursor() throws Exception {
		when(messageService.findHistory(10L, EMAIL, null, 50))
				.thenReturn(new MessageHistoryResponse(List.of(MENSAGEM), true, 50L));

		mockMvc.perform(get("/api/v1/chats/10/messages").principal(LOGADO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages.length()").value(1))
				.andExpect(jsonPath("$.hasMore").value(true))
				.andExpect(jsonPath("$.nextBefore").value(50));
	}

	@Test
	void getDeveRepassarCursorETamanho() throws Exception {
		when(messageService.findHistory(10L, EMAIL, 50L, 20))
				.thenReturn(new MessageHistoryResponse(List.of(), false, null));

		mockMvc.perform(get("/api/v1/chats/10/messages?before=50&size=20").principal(LOGADO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasMore").value(false));

		verify(messageService).findHistory(10L, EMAIL, 50L, 20);
	}

	@Test
	void getComTamanhoForaDoLimiteDeveRetornar400() throws Exception {
		mockMvc.perform(get("/api/v1/chats/10/messages?size=0").principal(LOGADO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.size").exists());

		mockMvc.perform(get("/api/v1/chats/10/messages?size=101").principal(LOGADO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.size").exists());

		verifyNoInteractions(messageService);
	}

	@Test
	void getComCursorInvalidoDeveRetornar400() throws Exception {
		mockMvc.perform(get("/api/v1/chats/10/messages?before=0").principal(LOGADO))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.before").exists());

		mockMvc.perform(get("/api/v1/chats/10/messages?before=abc").principal(LOGADO))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchReadDeveRetornarQuantidadeMarcada() throws Exception {
		when(messageService.markAsRead(10L, EMAIL)).thenReturn(new MarkAsReadResponse(3));

		mockMvc.perform(patch("/api/v1/chats/10/messages/read").principal(LOGADO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.markedAsRead").value(3));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enviar(String body) {
		return post("/api/v1/chats/10/messages")
				.principal(LOGADO)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

}
