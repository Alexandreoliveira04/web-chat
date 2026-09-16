package br.edu.webchat;

import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo minimo do MVP (spec secao 20), de ponta a ponta sobre HTTP real:
 * cadastro, login, criacao de conversa, envio de mensagem, historico e leitura.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MvpFlowIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ChatRepository chatRepository;

	@Autowired
	private MessageRepository messageRepository;

	@BeforeEach
	@AfterEach
	void limparBase() {
		messageRepository.deleteAll();
		chatRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void doisColaboradoresSeCadastramConversamEConsultamOHistorico() {
		rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

		// 1. cadastro (publico)
		ResponseEntity<JsonNode> cadastro = post("/api/v1/auth/register", null,
				Map.of("name", "Alexandre", "email", "alexandre@email.com", "password", "123456"));
		assertThat(cadastro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(cadastro.getBody().get("role").asText()).isEqualTo("USER");
		assertThat(cadastro.getBody().has("password")).isFalse();

		post("/api/v1/auth/register", null,
				Map.of("name", "Maria", "email", "maria@email.com", "password", "123456"));

		// 2. login
		String alexandre = login("alexandre@email.com");
		String maria = login("maria@email.com");

		// 3. endpoint protegido exige token
		assertThat(get("/api/v1/users", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		Long mariaId = get("/api/v1/users/me", maria).getBody().get("id").asLong();

		// 4. criacao da conversa
		ResponseEntity<JsonNode> conversa = post("/api/v1/chats", alexandre, Map.of("participantId", mariaId));
		assertThat(conversa.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		long chatId = conversa.getBody().get("id").asLong();

		// 5. envio de mensagens
		assertThat(post("/api/v1/chats/" + chatId + "/messages", alexandre, Map.of("content", "oi Maria"))
				.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		post("/api/v1/chats/" + chatId + "/messages", alexandre, Map.of("content", "tudo bem?"));
		post("/api/v1/chats/" + chatId + "/messages", maria, Map.of("content", "tudo sim!"));

		// 6. persistencia no banco
		assertThat(messageRepository.count()).isEqualTo(3);

		// 7. listagem com ultima mensagem e nao lidas
		JsonNode listaDaMaria = get("/api/v1/chats", maria).getBody();
		assertThat(listaDaMaria).hasSize(1);
		assertThat(listaDaMaria.get(0).get("lastMessage").get("content").asText()).isEqualTo("tudo sim!");
		assertThat(listaDaMaria.get(0).get("unreadCount").asInt()).isEqualTo(2);

		// 8. historico em ordem cronologica
		JsonNode historico = get("/api/v1/chats/" + chatId + "/messages", maria).getBody();
		assertThat(historico.get("messages")).hasSize(3);
		assertThat(historico.get("messages").get(0).get("content").asText()).isEqualTo("oi Maria");
		assertThat(historico.get("messages").get(2).get("content").asText()).isEqualTo("tudo sim!");
		assertThat(historico.get("hasMore").asBoolean()).isFalse();

		// 9. leitura
		ResponseEntity<JsonNode> leitura = exchange(HttpMethod.PATCH,
				"/api/v1/chats/" + chatId + "/messages/read", maria, null);
		assertThat(leitura.getBody().get("markedAsRead").asInt()).isEqualTo(2);
		assertThat(get("/api/v1/chats", maria).getBody().get(0).get("unreadCount").asInt()).isZero();

		// 10. quem nao participa nao enxerga a conversa
		post("/api/v1/auth/register", null,
				Map.of("name", "Joao", "email", "joao@email.com", "password", "123456"));
		assertThat(get("/api/v1/chats/" + chatId, login("joao@email.com")).getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);
	}

	private String login(String email) {
		ResponseEntity<JsonNode> response = post("/api/v1/auth/login", null,
				Map.of("email", email, "password", "123456"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody().get("token").asText();
	}

	private ResponseEntity<JsonNode> get(String path, String token) {
		return exchange(HttpMethod.GET, path, token, null);
	}

	private ResponseEntity<JsonNode> post(String path, String token, Object body) {
		return exchange(HttpMethod.POST, path, token, body);
	}

	private ResponseEntity<JsonNode> exchange(HttpMethod method, String path, String token, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			headers.setBearerAuth(token);
		}

		return rest.exchange("http://localhost:" + port + path, method, new HttpEntity<>(body, headers),
				JsonNode.class);
	}

}
