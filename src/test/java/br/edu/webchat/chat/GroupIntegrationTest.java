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

/** Fluxo de grupos com a cadeia de seguranca e o banco reais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupIntegrationTest {

	private static final String ANA = "ana@email.com";
	private static final String BRUNO = "bruno@email.com";
	private static final String CARLA = "carla@email.com";
	private static final String DAVI = "davi@email.com";

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

	private Long anaId;
	private Long brunoId;
	private Long carlaId;
	private Long daviId;

	@BeforeEach
	void prepararUsuarios() {
		limparBase();
		anaId = criar("Ana", ANA);
		brunoId = criar("Bruno", BRUNO);
		carlaId = criar("Carla", CARLA);
		daviId = criar("Davi", DAVI);
	}

	@AfterEach
	void limparBase() {
		messageRepository.deleteAll();
		chatRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void criarGrupoIncluiODonoEApareceParaTodosOsParticipantes() throws Exception {
		Long grupo = criarGrupo(ANA, "Time de TCC", brunoId, carlaId);

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(BRUNO)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("GROUP"))
				.andExpect(jsonPath("$.name").value("Time de TCC"))
				.andExpect(jsonPath("$.ownerId").value(anaId))
				.andExpect(jsonPath("$.participants.length()").value(3));

		mockMvc.perform(get("/api/v1/chats").header("Authorization", bearer(CARLA)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(grupo));

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(DAVI)))
				.andExpect(status().isForbidden());
	}

	@Test
	void mensagensDoGrupoTemLeituraIndependentePorParticipante() throws Exception {
		Long grupo = criarGrupo(ANA, "Estudos", brunoId, carlaId);

		mockMvc.perform(enviar(ANA, grupo, "bom dia")).andExpect(status().isCreated());
		mockMvc.perform(enviar(ANA, grupo, "alguem ai?")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(BRUNO)))
				.andExpect(jsonPath("$.unreadCount").value(2));
		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(CARLA)))
				.andExpect(jsonPath("$.unreadCount").value(2));

		mockMvc.perform(patch("/api/v1/chats/" + grupo + "/messages/read").header("Authorization", bearer(BRUNO)))
				.andExpect(jsonPath("$.markedAsRead").value(2));

		// Bruno leu; Carla continua com as duas nao lidas
		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(BRUNO)))
				.andExpect(jsonPath("$.unreadCount").value(0));
		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(CARLA)))
				.andExpect(jsonPath("$.unreadCount").value(2));

		// so vira ✓✓ para a Ana quando todos os outros tiverem lido
		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(ANA)))
				.andExpect(jsonPath("$.lastReadByOthersMessageId").isEmpty());

		mockMvc.perform(patch("/api/v1/chats/" + grupo + "/messages/read").header("Authorization", bearer(CARLA)));

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(ANA)))
				.andExpect(jsonPath("$.lastReadByOthersMessageId").isNotEmpty());
	}

	@Test
	void somenteODonoAdministraOGrupo() throws Exception {
		Long grupo = criarGrupo(ANA, "Projeto", brunoId, carlaId);

		mockMvc.perform(renomear(BRUNO, grupo, "Sequestrado")).andExpect(status().isForbidden());
		mockMvc.perform(adicionar(BRUNO, grupo, daviId)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/" + carlaId)
						.header("Authorization", bearer(BRUNO)))
				.andExpect(status().isForbidden());

		mockMvc.perform(renomear(ANA, grupo, "  Projeto Final  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Projeto Final"));

		mockMvc.perform(adicionar(ANA, grupo, daviId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participants.length()").value(4));

		mockMvc.perform(adicionar(ANA, grupo, daviId)).andExpect(status().isConflict());

		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/" + carlaId)
						.header("Authorization", bearer(ANA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participants.length()").value(3));

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(CARLA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void donoNaoPodeSerRemovidoEParticipanteInexistenteResultaEm404() throws Exception {
		Long grupo = criarGrupo(ANA, "Projeto", brunoId);

		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/" + anaId)
						.header("Authorization", bearer(ANA)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/" + daviId)
						.header("Authorization", bearer(ANA)))
				.andExpect(status().isNotFound());
	}

	@Test
	void sairDoGrupoTransfereADonoParaOParticipanteMaisAntigo() throws Exception {
		Long grupo = criarGrupo(ANA, "Projeto", brunoId, carlaId);

		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/me").header("Authorization", bearer(ANA)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(ANA)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/chats/" + grupo).header("Authorization", bearer(BRUNO)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").value(brunoId))
				.andExpect(jsonPath("$.participants.length()").value(2));
	}

	@Test
	void grupoSemParticipantesEApagadoComAsMensagens() throws Exception {
		Long grupo = criarGrupo(ANA, "Temporario", brunoId);
		mockMvc.perform(enviar(ANA, grupo, "ate mais")).andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/me").header("Authorization", bearer(BRUNO)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/chats/" + grupo + "/participants/me").header("Authorization", bearer(ANA)))
				.andExpect(status().isNoContent());

		assertThat(chatRepository.findById(grupo)).isEmpty();
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	void operacoesDeGrupoNaoValemParaConversaIndividual() throws Exception {
		String body = mockMvc.perform(post("/api/v1/chats")
						.header("Authorization", bearer(ANA))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"participantId\":" + brunoId + "}"))
				.andReturn().getResponse().getContentAsString();
		Long individual = objectMapper.readTree(body).get("id").asLong();

		mockMvc.perform(renomear(ANA, individual, "Nao pode")).andExpect(status().isBadRequest());
		mockMvc.perform(adicionar(ANA, individual, carlaId)).andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/v1/chats/" + individual + "/participants/me").header("Authorization", bearer(ANA)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void validacoesDaCriacaoDeGrupo() throws Exception {
		mockMvc.perform(criarGrupoRequest(ANA, "{\"name\":\"  \",\"participantIds\":[" + brunoId + "]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.name").exists());

		mockMvc.perform(criarGrupoRequest(ANA, "{\"name\":\"Sozinha\",\"participantIds\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.participantIds").exists());

		// so o proprio usuario na lista: nao sobra ninguem para o grupo
		mockMvc.perform(criarGrupoRequest(ANA, "{\"name\":\"Sozinha\",\"participantIds\":[" + anaId + "]}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(criarGrupoRequest(ANA, "{\"name\":\"Fantasma\",\"participantIds\":[999999]}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/chats/groups")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Sem token\",\"participantIds\":[" + brunoId + "]}"))
				.andExpect(status().isUnauthorized());
	}

	private Long criarGrupo(String dono, String nome, Long... participantes) throws Exception {
		String ids = String.join(",", java.util.Arrays.stream(participantes).map(String::valueOf).toList());
		String body = mockMvc.perform(criarGrupoRequest(dono, "{\"name\":\"" + nome + "\",\"participantIds\":[" + ids + "]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerId").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("id").asLong();
	}

	private MockHttpServletRequestBuilder criarGrupoRequest(String email, String body) {
		return post("/api/v1/chats/groups")
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
	}

	private MockHttpServletRequestBuilder renomear(String email, Long chatId, String nome) throws Exception {
		return patch("/api/v1/chats/" + chatId)
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", nome)));
	}

	private MockHttpServletRequestBuilder adicionar(String email, Long chatId, Long userId) {
		return post("/api/v1/chats/" + chatId + "/participants")
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":" + userId + "}");
	}

	private MockHttpServletRequestBuilder enviar(String email, Long chatId, String conteudo) throws Exception {
		return post("/api/v1/chats/" + chatId + "/messages")
				.header("Authorization", bearer(email))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", conteudo)));
	}

	private Long criar(String nome, String email) {
		return userService.create(new CreateUserRequest(nome, email, "123456")).id();
	}

	private String bearer(String email) {
		return "Bearer " + jwtService.generateToken(email);
	}

}
