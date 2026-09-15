package br.edu.webchat.chat;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.repository.UserRepository;
import br.edu.webchat.user.service.UserService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo de conversas com a cadeia de seguranca e o banco reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ChatRepository chatRepository;

	@Autowired
	private JwtService jwtService;

	private Long alexandreId;
	private Long mariaId;

	@BeforeEach
	void prepararUsuarios() {
		limparBase();
		alexandreId = userService.create(new CreateUserRequest("Alexandre", "alexandre@email.com", "123456")).id();
		mariaId = userService.create(new CreateUserRequest("Maria", "maria@email.com", "123456")).id();
		userService.create(new CreateUserRequest("Joao", "joao@email.com", "123456"));
	}

	// chat_participants referencia users: as conversas precisam sair antes dos usuarios,
	// inclusive para nao quebrar a limpeza de outras classes de teste que usam o mesmo banco.
	@AfterEach
	void limparBase() {
		chatRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void endpointsDeChatExigemToken() throws Exception {
		mockMvc.perform(get("/api/v1/chats")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/chats")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"participantId\":" + mariaId + "}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/chats/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void fluxoDeCriacaoReaproveitamentoEListagem() throws Exception {
		String location = mockMvc.perform(criarConversa("alexandre@email.com", mariaId))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.participants.length()").value(2))
				.andExpect(jsonPath("$.participants[0].id").value(alexandreId))
				.andExpect(jsonPath("$.participants[1].id").value(mariaId))
				.andReturn().getResponse().getHeader("Location");

		Long chatId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

		mockMvc.perform(criarConversa("maria@email.com", alexandreId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(chatId));

		assertThat(chatRepository.count()).isEqualTo(1);

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer("maria@email.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(chatId));

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer("joao@email.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/api/v1/chats/" + chatId).header("Authorization", bearer("alexandre@email.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participants[1].email").value("maria@email.com"));
	}

	@Test
	void naoParticipanteRecebe403AoConsultarConversa() throws Exception {
		String location = mockMvc.perform(criarConversa("alexandre@email.com", mariaId))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", bearer("joao@email.com")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Voce nao participa desta conversa"));
	}

	@Test
	void conversaInexistenteRetorna404() throws Exception {
		mockMvc.perform(get("/api/v1/chats/999999").header("Authorization", bearer("alexandre@email.com")))
				.andExpect(status().isNotFound());
	}

	@Test
	void conversaConsigoMesmoRetorna400() throws Exception {
		mockMvc.perform(criarConversa("alexandre@email.com", alexandreId))
				.andExpect(status().isBadRequest());
	}

	@Test
	void conversaComUsuarioInexistenteRetorna404() throws Exception {
		mockMvc.perform(criarConversa("alexandre@email.com", 999999L))
				.andExpect(status().isNotFound());
	}

	private MockHttpServletRequestBuilder criarConversa(String email, Long participantId) {
		return post("/api/v1/chats")
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"participantId\":" + participantId + "}");
	}

	private String bearer(String email) {
		return "Bearer " + jwtService.generateToken(email);
	}

}
