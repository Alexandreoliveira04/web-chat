package br.edu.webchat.chat;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.repository.UserRepository;
import br.edu.webchat.user.service.UserService;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Edicao e exclusao da propria mensagem, com a cadeia de seguranca e o banco reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EditAndDeleteMessageIntegrationTest {

	private static final String ANA = "ana@email.com";
	private static final String BRUNO = "bruno@email.com";
	private static final String CARLA = "carla@email.com";

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

	private Long chatId;

	@BeforeEach
	void prepararConversa() throws Exception {
		limparBase();
		userService.create(new CreateUserRequest("Ana", ANA, "123456"));
		Long brunoId = userService.create(new CreateUserRequest("Bruno", BRUNO, "123456")).id();
		userService.create(new CreateUserRequest("Carla", CARLA, "123456"));

		String body = mockMvc.perform(post("/api/v1/chats")
						.header("Authorization", bearer(ANA))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"participantId\":" + brunoId + "}"))
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
	void autorEditaAPropriaMensagemEEllaFicaMarcadaComoEditada() throws Exception {
		Long messageId = enviar(ANA, "mensagen com errro");

		mockMvc.perform(editar(ANA, messageId, "  mensagem corrigida  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value("mensagem corrigida"))
				.andExpect(jsonPath("$.editedAt").isNotEmpty())
				.andExpect(jsonPath("$.deletedAt").isEmpty());

		mockMvc.perform(get(mensagens()).header("Authorization", bearer(BRUNO)))
				.andExpect(jsonPath("$.messages[0].content").value("mensagem corrigida"))
				.andExpect(jsonPath("$.messages[0].editedAt").isNotEmpty());
	}

	@Test
	void autorApagaAPropriaMensagemEElaPermaneceNoHistoricoSemConteudo() throws Exception {
		Long messageId = enviar(ANA, "mensagem secreta");
		enviar(BRUNO, "resposta");

		mockMvc.perform(apagar(ANA, messageId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(""))
				.andExpect(jsonPath("$.deletedAt").isNotEmpty());

		mockMvc.perform(get(mensagens()).header("Authorization", bearer(BRUNO)))
				.andExpect(jsonPath("$.messages.length()").value(2))
				.andExpect(jsonPath("$.messages[0].content").value(""))
				.andExpect(jsonPath("$.messages[0].deletedAt").isNotEmpty());

		assertThat(messageRepository.count()).isEqualTo(2);
	}

	@Test
	void apagarEIdempotenteEEditarMensagemApagadaERecusado() throws Exception {
		Long messageId = enviar(ANA, "vai sumir");

		mockMvc.perform(apagar(ANA, messageId)).andExpect(status().isOk());
		mockMvc.perform(apagar(ANA, messageId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deletedAt").isNotEmpty());

		mockMvc.perform(editar(ANA, messageId, "ressuscitar"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void somenteOAutorEditaOuApaga() throws Exception {
		Long messageId = enviar(ANA, "minha mensagem");

		mockMvc.perform(editar(BRUNO, messageId, "editada por outro")).andExpect(status().isForbidden());
		mockMvc.perform(apagar(BRUNO, messageId)).andExpect(status().isForbidden());

		// quem nem participa da conversa nao chega na mensagem
		mockMvc.perform(editar(CARLA, messageId, "intrusa")).andExpect(status().isForbidden());
		mockMvc.perform(apagar(CARLA, messageId)).andExpect(status().isForbidden());

		mockMvc.perform(get(mensagens()).header("Authorization", bearer(ANA)))
				.andExpect(jsonPath("$.messages[0].content").value("minha mensagem"))
				.andExpect(jsonPath("$.messages[0].editedAt").isEmpty());
	}

	@Test
	void mensagemInexistenteOuDeOutraConversaResultaEm404() throws Exception {
		Long outroChat = chatId + 999;

		mockMvc.perform(editar(ANA, 999999L, "nada")).andExpect(status().isNotFound());
		mockMvc.perform(apagar(ANA, 999999L)).andExpect(status().isNotFound());

		Long messageId = enviar(ANA, "mensagem valida");
		mockMvc.perform(patch("/api/v1/chats/" + outroChat + "/messages/" + messageId)
						.header("Authorization", bearer(ANA))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"nada\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void edicaoValidaOConteudo() throws Exception {
		Long messageId = enviar(ANA, "mensagem");

		mockMvc.perform(editar(ANA, messageId, "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.content").exists());

		mockMvc.perform(editar(ANA, messageId, "a".repeat(2001)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void mensagemApagadaContinuaContandoNaListagemComoUltimaMensagem() throws Exception {
		Long messageId = enviar(ANA, "ultima mensagem");

		mockMvc.perform(apagar(ANA, messageId)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(BRUNO)))
				.andExpect(jsonPath("$[0].lastMessage.id").value(messageId))
				.andExpect(jsonPath("$[0].lastMessage.deletedAt").isNotEmpty());
	}

	private Long enviar(String email, String conteudo) throws Exception {
		String body = mockMvc.perform(post(mensagens())
						.header("Authorization", bearer(email))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("content", conteudo))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	private MockHttpServletRequestBuilder editar(String email, Long messageId, String conteudo) throws Exception {
		return patch(mensagens() + "/" + messageId)
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", conteudo)));
	}

	private MockHttpServletRequestBuilder apagar(String email, Long messageId) {
		return delete(mensagens() + "/" + messageId).header("Authorization", bearer(email));
	}

	private String mensagens() {
		return "/api/v1/chats/" + chatId + "/messages";
	}

	private String bearer(String email) {
		return "Bearer " + jwtService.generateToken(email);
	}

}
