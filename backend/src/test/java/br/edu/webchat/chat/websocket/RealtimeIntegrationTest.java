package br.edu.webchat.chat.websocket;

import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.PresenceResponse;
import br.edu.webchat.chat.dto.ReadReceiptResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.shared.exception.ApiError;
import br.edu.webchat.support.StompIntegrationTest;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.ProfileResponse;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tempo real com servidor, WebSocket, STOMP, seguranca e banco reais. */
class RealtimeIntegrationTest extends StompIntegrationTest {

	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String MARIA = "maria@email.com";
	private static final String JOAO = "joao@email.com";

	@Autowired
	private UserService userService;

	private Long alexandreId;
	private Long mariaId;
	private Long chatId;

	@BeforeEach
	void setUp() {
		limparBase();
		alexandreId = userService.create(new CreateUserRequest("Alexandre", ALEXANDRE, "123456")).id();
		mariaId = userService.create(new CreateUserRequest("Maria", MARIA, "123456")).id();
		userService.create(new CreateUserRequest("Joao", JOAO, "123456"));

		Map<?, ?> chat = rest.exchange(url("/api/v1/chats"), HttpMethod.POST,
				json(ALEXANDRE, Map.of("participantId", mariaId)), Map.class).getBody();
		chatId = ((Number) chat.get("id")).longValue();
	}

	@Test
	void conexaoSemTokenOuComTokenInvalidoERecusada() {
		assertThatThrownBy(() -> connect(null)).isInstanceOfAny(ExecutionException.class, TimeoutException.class);
		assertThatThrownBy(() -> connect("Bearer token-invalido"))
				.isInstanceOfAny(ExecutionException.class, TimeoutException.class);
	}

	@Test
	void mensagemEnviadaPorRestChegaEmTempoRealParaOsDoisParticipantes() throws Exception {
		BlockingQueue<MessageResponse> paraMaria = subscribe(conectar(MARIA), MARIA, "/user/queue/messages", MessageResponse.class);
		BlockingQueue<MessageResponse> paraAlexandre = subscribe(conectar(ALEXANDRE), ALEXANDRE, "/user/queue/messages", MessageResponse.class);
		BlockingQueue<MessageResponse> paraJoao = subscribe(conectar(JOAO), JOAO, "/user/queue/messages", MessageResponse.class);

		rest.exchange(url("/api/v1/chats/" + chatId + "/messages"), HttpMethod.POST,
				json(ALEXANDRE, Map.of("content", "oi Maria")), Map.class);

		MessageResponse recebida = paraMaria.poll(5, TimeUnit.SECONDS);
		assertThat(recebida).isNotNull();
		assertThat(recebida.content()).isEqualTo("oi Maria");
		assertThat(recebida.senderId()).isEqualTo(alexandreId);
		assertThat(recebida.chatId()).isEqualTo(chatId);

		assertThat(paraAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(recebida);
		assertThat(paraJoao.poll(500, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void mensagemEnviadaPorStompEPersistidaEEntregue() throws Exception {
		StompSession maria = conectar(MARIA);
		BlockingQueue<MessageResponse> paraMaria = subscribe(maria, MARIA, "/user/queue/messages", MessageResponse.class);
		StompSession alexandre = conectar(ALEXANDRE);
		BlockingQueue<MessageResponse> paraAlexandre = subscribe(alexandre, ALEXANDRE, "/user/queue/messages", MessageResponse.class);

		alexandre.send("/app/chats/" + chatId + "/messages", new SendMessageRequest("  via websocket  "));

		MessageResponse recebida = paraMaria.poll(5, TimeUnit.SECONDS);
		assertThat(recebida).isNotNull();
		assertThat(recebida.content()).isEqualTo("via websocket");
		assertThat(recebida.senderId()).isEqualTo(alexandreId);
		assertThat(paraAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(recebida);
		assertThat(messageRepository.findById(recebida.id())).isPresent();
	}

	@Test
	void erroNoEnvioPorStompVaiSomenteParaQuemEnviou() throws Exception {
		StompSession joao = conectar(JOAO);
		BlockingQueue<ApiError> errosDoJoao = subscribe(joao, JOAO, "/user/queue/errors", ApiError.class);
		StompSession alexandre = conectar(ALEXANDRE);
		BlockingQueue<ApiError> errosDoAlexandre = subscribe(alexandre, ALEXANDRE, "/user/queue/errors", ApiError.class);
		BlockingQueue<MessageResponse> paraMaria = subscribe(conectar(MARIA), MARIA, "/user/queue/messages", MessageResponse.class);

		joao.send("/app/chats/" + chatId + "/messages", new SendMessageRequest("intruso"));
		ApiError proibido = errosDoJoao.poll(5, TimeUnit.SECONDS);
		assertThat(proibido).isNotNull();
		assertThat(proibido.status()).isEqualTo(403);
		assertThat(proibido.path()).isEqualTo("/app/chats/" + chatId + "/messages");

		alexandre.send("/app/chats/" + chatId + "/messages", new SendMessageRequest("   "));
		ApiError invalido = errosDoAlexandre.poll(5, TimeUnit.SECONDS);
		assertThat(invalido).isNotNull();
		assertThat(invalido.status()).isEqualTo(400);
		assertThat(invalido.fields()).containsKey("content");

		assertThat(paraMaria.poll(500, TimeUnit.MILLISECONDS)).isNull();
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	void leituraAvisaQuemEnviouEmTempoReal() throws Exception {
		rest.exchange(url("/api/v1/chats/" + chatId + "/messages"), HttpMethod.POST,
				json(ALEXANDRE, Map.of("content", "leu?")), Map.class);

		BlockingQueue<ReadReceiptResponse> paraAlexandre =
				subscribe(conectar(ALEXANDRE), ALEXANDRE, "/user/queue/read", ReadReceiptResponse.class);

		rest.exchange(url("/api/v1/chats/" + chatId + "/messages/read"), HttpMethod.PATCH, json(MARIA, null), Map.class);

		ReadReceiptResponse recibo = paraAlexandre.poll(5, TimeUnit.SECONDS);
		assertThat(recibo).isNotNull();
		assertThat(recibo.chatId()).isEqualTo(chatId);
		assertThat(recibo.readerId()).isEqualTo(mariaId);
		assertThat(recibo.markedAsRead()).isEqualTo(1);
		assertThat(recibo.lastReadMessageId()).isNotNull();
	}

	@Test
	void presencaAcompanhaAsConexoes() throws Exception {
		BlockingQueue<PresenceResponse> presenca = subscribe(conectar(ALEXANDRE), ALEXANDRE, "/topic/presence", PresenceResponse.class);

		StompSession maria = conectar(MARIA);
		assertThat(presenca.poll(5, TimeUnit.SECONDS)).isEqualTo(new PresenceResponse(mariaId, UserStatus.ONLINE));
		assertThat(userRepository.findById(mariaId).orElseThrow().getStatus()).isEqualTo(UserStatus.ONLINE);

		maria.disconnect();
		assertThat(presenca.poll(5, TimeUnit.SECONDS)).isEqualTo(new PresenceResponse(mariaId, UserStatus.OFFLINE));
		assertThat(userRepository.findById(mariaId).orElseThrow().getStatus()).isEqualTo(UserStatus.OFFLINE);
	}

	@Test
	void cadastroAvisaOsConectadosParaANovaPessoaAparecerNaLista() throws Exception {
		BlockingQueue<UserResponse> novos = subscribe(conectar(ALEXANDRE), ALEXANDRE, "/topic/users", UserResponse.class);

		rest.postForEntity(url("/api/v1/auth/register"),
				new CreateUserRequest("Bia", "bia@email.com", "123456"), Map.class);

		UserResponse bia = novos.poll(5, TimeUnit.SECONDS);
		assertThat(bia).isNotNull();
		assertThat(bia.name()).isEqualTo("Bia");
		assertThat(bia.email()).isEqualTo("bia@email.com");
		assertThat(bia.status()).isEqualTo(UserStatus.OFFLINE);
	}

	@Test
	void trocaDeNomeChegaATodosOsConectados() throws Exception {
		BlockingQueue<ProfileResponse> perfis = subscribe(conectar(ALEXANDRE), ALEXANDRE, "/topic/profiles", ProfileResponse.class);

		rest.exchange(url("/api/v1/users/me"), HttpMethod.PUT, json(MARIA, Map.of("name", "Maria Silva")), Map.class);

		assertThat(perfis.poll(5, TimeUnit.SECONDS)).isEqualTo(new ProfileResponse(mariaId, "Maria Silva", null));
	}

}
