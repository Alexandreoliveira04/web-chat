package br.edu.webchat.chat.controller;

import br.edu.webchat.chat.dto.ChatResponse;
import br.edu.webchat.chat.dto.CreateChatRequest;
import br.edu.webchat.chat.dto.CreateChatResult;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.ParticipantResponse;
import br.edu.webchat.chat.entity.ChatType;
import br.edu.webchat.chat.service.ChatService;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.UserStatus;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
// addFilters = false: estes testes verificam o controller, nao a seguranca.
// A cadeia de seguranca real e coberta por ChatIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

	private static final String EMAIL = "alexandre@email.com";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatService chatService;

	private static final Authentication LOGADO = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

	private static final ChatResponse CONVERSA = new ChatResponse(10L, ChatType.DIRECT, null, null,
			List.of(new ParticipantResponse(1L, "Alexandre", EMAIL, UserStatus.OFFLINE, null),
					new ParticipantResponse(2L, "Maria", "maria@email.com", UserStatus.ONLINE, null)),
			new MessageResponse(99L, 10L, 2L, "oi, tudo bem?", Instant.parse("2026-09-14T22:05:00Z"), null, null),
			3,
			98L,
			Instant.parse("2026-09-14T22:00:00Z"), Instant.parse("2026-09-14T22:00:00Z"));

	@Test
	void postDeveRetornar201ComLocationQuandoCriaAConversa() throws Exception {
		when(chatService.create(eq(EMAIL), any(CreateChatRequest.class)))
				.thenReturn(new CreateChatResult(CONVERSA, true));

		mockMvc.perform(criarConversa("{\"participantId\":2}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/chats/10"))
				.andExpect(jsonPath("$.id").value(10))
				.andExpect(jsonPath("$.participants.length()").value(2))
				.andExpect(jsonPath("$.participants[1].name").value("Maria"))
				.andExpect(jsonPath("$.participants[1].status").value("ONLINE"))
				.andExpect(jsonPath("$.participants[0].password").doesNotExist());
	}

	@Test
	void postDeveRetornar200SemLocationQuandoReaproveitaAConversa() throws Exception {
		when(chatService.create(eq(EMAIL), any(CreateChatRequest.class)))
				.thenReturn(new CreateChatResult(CONVERSA, false));

		mockMvc.perform(criarConversa("{\"participantId\":2}"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.id").value(10));
	}

	@Test
	void postSemParticipanteDeveRetornar400() throws Exception {
		mockMvc.perform(criarConversa("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.participantId").exists());
	}

	@Test
	void postComParticipanteNaoPositivoDeveRetornar400() throws Exception {
		mockMvc.perform(criarConversa("{\"participantId\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.participantId").exists());
	}

	@Test
	void postConsigoMesmoDeveRetornar400() throws Exception {
		when(chatService.create(eq(EMAIL), any(CreateChatRequest.class)))
				.thenThrow(new BadRequestException("Nao e possivel iniciar uma conversa consigo mesmo"));

		mockMvc.perform(criarConversa("{\"participantId\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Nao e possivel iniciar uma conversa consigo mesmo"));
	}

	@Test
	void getDeveListarAsConversas() throws Exception {
		when(chatService.findMyChats(EMAIL, null)).thenReturn(List.of(CONVERSA));

		mockMvc.perform(get("/api/v1/chats").principal(LOGADO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(10))
				.andExpect(jsonPath("$[0].lastMessage.content").value("oi, tudo bem?"))
				.andExpect(jsonPath("$[0].lastMessage.senderId").value(2))
				.andExpect(jsonPath("$[0].unreadCount").value(3))
				.andExpect(jsonPath("$[0].lastReadByOthersMessageId").value(98));
	}

	@Test
	void getPorIdDeveRetornarAConversa() throws Exception {
		when(chatService.findById(10L, EMAIL)).thenReturn(CONVERSA);

		mockMvc.perform(get("/api/v1/chats/10").principal(LOGADO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participants[0].email").value(EMAIL));
	}

	@Test
	void getPorIdSemParticiparDeveRetornar403() throws Exception {
		when(chatService.findById(10L, EMAIL)).thenThrow(new ForbiddenException("Voce nao participa desta conversa"));

		mockMvc.perform(get("/api/v1/chats/10").principal(LOGADO))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void getPorIdInexistenteDeveRetornar404() throws Exception {
		when(chatService.findById(99L, EMAIL)).thenThrow(new NotFoundException("Conversa nao encontrada: 99"));

		mockMvc.perform(get("/api/v1/chats/99").principal(LOGADO))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.path").value("/api/v1/chats/99"));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder criarConversa(String body) {
		return post("/api/v1/chats")
				.principal(LOGADO)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

}
