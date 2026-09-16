package br.edu.webchat.chat;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.repository.UserRepository;
import br.edu.webchat.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo de mensagens com a cadeia de seguranca e o banco reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageIntegrationTest {

	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String MARIA = "maria@email.com";
	private static final String JOAO = "joao@email.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ChatRepository chatRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private JwtService jwtService;

	private Long alexandreId;
	private Long mariaId;
	private Long chatId;

	@BeforeEach
	void prepararConversa() throws Exception {
		limparBase();
		alexandreId = userService.create(new CreateUserRequest("Alexandre", ALEXANDRE, "123456")).id();
		mariaId = userService.create(new CreateUserRequest("Maria", MARIA, "123456")).id();
		userService.create(new CreateUserRequest("Joao", JOAO, "123456"));

		String body = mockMvc.perform(post("/api/v1/chats")
						.header("Authorization", bearer(ALEXANDRE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"participantId\":" + mariaId + "}"))
				.andReturn().getResponse().getContentAsString();
		chatId = objectMapper.readTree(body).get("id").asLong();
	}

	@AfterEach
	void limparBase() {
		messageRepository.deleteAll();
		chatRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void endpointsDeMensagemExigemToken() throws Exception {
		mockMvc.perform(get(mensagens())).andExpect(status().isUnauthorized());
		mockMvc.perform(post(mensagens()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"oi\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch(mensagens() + "/read")).andExpect(status().isUnauthorized());
	}

	@Test
	void fluxoCompletoDeEnvioHistoricoELeitura() throws Exception {
		mockMvc.perform(enviar(ALEXANDRE, "oi Maria"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.senderId").value(alexandreId))
				.andExpect(jsonPath("$.chatId").value(chatId));
		mockMvc.perform(enviar(ALEXANDRE, "tudo bem?")).andExpect(status().isCreated());
		mockMvc.perform(enviar(MARIA, "tudo sim!")).andExpect(status().isCreated());
		mockMvc.perform(enviar(ALEXANDRE, "que bom")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(MARIA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].lastMessage.content").value("que bom"))
				.andExpect(jsonPath("$[0].lastMessage.senderId").value(alexandreId))
				.andExpect(jsonPath("$[0].unreadCount").value(3));

		mockMvc.perform(get("/api/v1/chats/" + chatId).header("Authorization", bearer(ALEXANDRE)))
				.andExpect(jsonPath("$.unreadCount").value(1));

		mockMvc.perform(get(mensagens()).header("Authorization", bearer(MARIA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages.length()").value(4))
				.andExpect(jsonPath("$.messages[0].content").value("oi Maria"))
				.andExpect(jsonPath("$.messages[3].content").value("que bom"))
				.andExpect(jsonPath("$.hasMore").value(false))
				.andExpect(jsonPath("$.nextBefore").isEmpty());

		mockMvc.perform(patch(mensagens() + "/read").header("Authorization", bearer(MARIA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.markedAsRead").value(3));

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(MARIA)))
				.andExpect(jsonPath("$[0].unreadCount").value(0))
				.andExpect(jsonPath("$[0].lastReadByOthersMessageId").isEmpty());

		// a leitura da Maria nao mexe nas nao lidas do Alexandre, mas marca o ✓✓ dele
		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(ALEXANDRE)))
				.andExpect(jsonPath("$[0].unreadCount").value(1))
				.andExpect(jsonPath("$[0].lastReadByOthersMessageId").isNotEmpty());
	}

	@Test
	void leituraDeUmParticipanteNaoZeraAsNaoLidasDoOutro() throws Exception {
		mockMvc.perform(enviar(ALEXANDRE, "primeira"));
		mockMvc.perform(enviar(MARIA, "resposta"));

		mockMvc.perform(patch(mensagens() + "/read").header("Authorization", bearer(MARIA)))
				.andExpect(jsonPath("$.markedAsRead").value(1));

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(MARIA)))
				.andExpect(jsonPath("$[0].unreadCount").value(0));
		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(ALEXANDRE)))
				.andExpect(jsonPath("$[0].unreadCount").value(1));

		mockMvc.perform(patch(mensagens() + "/read").header("Authorization", bearer(MARIA)))
				.andExpect(jsonPath("$.markedAsRead").value(0));
	}

	@Test
	void historicoDevePaginarPorCursorSemRepetirMensagens() throws Exception {
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(enviar(i % 2 == 0 ? MARIA : ALEXANDRE, "mensagem " + i));
		}

		JsonNode primeira = historico(MARIA, mensagens() + "?size=2");
		assertThat(conteudos(primeira)).containsExactly("mensagem 4", "mensagem 5");
		assertThat(primeira.get("hasMore").asBoolean()).isTrue();

		mockMvc.perform(enviar(ALEXANDRE, "mensagem nova no meio da rolagem"));

		JsonNode segunda = historico(MARIA, mensagens() + "?size=2&before=" + primeira.get("nextBefore").asLong());
		assertThat(conteudos(segunda)).containsExactly("mensagem 2", "mensagem 3");

		JsonNode terceira = historico(MARIA, mensagens() + "?size=2&before=" + segunda.get("nextBefore").asLong());
		assertThat(conteudos(terceira)).containsExactly("mensagem 1");
		assertThat(terceira.get("hasMore").asBoolean()).isFalse();
	}

	@Test
	void enviarMensagemDeveLevarAConversaParaOTopoDaLista() throws Exception {
		Long joaoId = userRepository.findByEmail(JOAO).orElseThrow().getId();
		mockMvc.perform(post("/api/v1/chats")
						.header("Authorization", bearer(ALEXANDRE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"participantId\":" + joaoId + "}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(ALEXANDRE)))
				.andExpect(jsonPath("$[1].id").value(chatId));

		mockMvc.perform(enviar(MARIA, "oi")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(ALEXANDRE)))
				.andExpect(jsonPath("$[0].id").value(chatId));
	}

	@Test
	void naoParticipanteRecebe403EmTodosOsEndpoints() throws Exception {
		mockMvc.perform(enviar(ALEXANDRE, "segredo")).andExpect(status().isCreated());

		mockMvc.perform(enviar(JOAO, "intruso")).andExpect(status().isForbidden());
		mockMvc.perform(get(mensagens()).header("Authorization", bearer(JOAO))).andExpect(status().isForbidden());
		mockMvc.perform(patch(mensagens() + "/read").header("Authorization", bearer(JOAO)))
				.andExpect(status().isForbidden());

		assertThat(messageRepository.count()).isEqualTo(1);
	}

	@Test
	void conteudoInvalidoEConversaInexistente() throws Exception {
		mockMvc.perform(enviar(ALEXANDRE, "   ")).andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/chats/999999/messages")
						.header("Authorization", bearer(ALEXANDRE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"oi\"}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(get(mensagens() + "?size=500").header("Authorization", bearer(ALEXANDRE)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.size").exists());
	}

	private MockHttpServletRequestBuilder enviar(String email, String content) throws Exception {
		return post(mensagens())
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("content", content)));
	}

	private JsonNode historico(String email, String url) throws Exception {
		String body = mockMvc.perform(get(url).header("Authorization", bearer(email)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	private static java.util.List<String> conteudos(JsonNode history) {
		java.util.List<String> result = new java.util.ArrayList<>();
		history.get("messages").forEach(message -> result.add(message.get("content").asText()));
		return result;
	}

	private String mensagens() {
		return "/api/v1/chats/" + chatId + "/messages";
	}

	private String bearer(String email) {
		return "Bearer " + jwtService.generateToken(email);
	}

}
