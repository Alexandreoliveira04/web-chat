package br.edu.webchat.chat.websocket;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.chat.dto.MessageResponse;
import br.edu.webchat.chat.dto.PresenceResponse;
import br.edu.webchat.chat.dto.ReadReceiptResponse;
import br.edu.webchat.chat.dto.SendMessageRequest;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.shared.exception.ApiError;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.repository.UserRepository;
import br.edu.webchat.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tempo real com servidor, WebSocket, STOMP, seguranca e banco reais. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeIntegrationTest {

	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String MARIA = "maria@email.com";
	private static final String JOAO = "joao@email.com";

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ChatRepository chatRepository;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private SimpUserRegistry userRegistry;

	private final RestTemplate rest = new RestTemplate(new JdkClientHttpRequestFactory());
	private final List<StompSession> sessions = new ArrayList<>();

	private WebSocketStompClient stompClient;
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

		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setObjectMapper(objectMapper);
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(converter);
	}

	@AfterEach
	void tearDown() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		aguardar(() -> userRegistry.getUserCount() == 0);
		limparBase();
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
		assertThat(recibo.readAt()).isBefore(Instant.now().plusSeconds(1));
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

	private StompSession conectar(String email) throws Exception {
		return connect("Bearer " + jwtService.generateToken(email));
	}

	private StompSession connect(String authorization) throws Exception {
		StompHeaders headers = new StompHeaders();
		if (authorization != null) {
			headers.add("Authorization", authorization);
		}

		CompletableFuture<StompSession> future = stompClient.connectAsync("ws://localhost:" + port + "/ws",
				new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() { });
		StompSession session = future.get(3, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	private <T> BlockingQueue<T> subscribe(StompSession session, String email, String destination, Class<T> type) {
		BlockingQueue<T> queue = new LinkedBlockingQueue<>();
		int esperadas = assinaturas(email, destination) + 1;
		session.subscribe(destination, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return type;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				queue.add(type.cast(payload));
			}
		});

		aguardar(() -> assinaturas(email, destination) >= esperadas);
		return queue;
	}

	private int assinaturas(String email, String destination) {
		return userRegistry.findSubscriptions(subscription ->
				subscription.getDestination().equals(destination)
						&& subscription.getSession().getUser().getName().equals(email)).size();
	}

	private static void aguardar(BooleanSupplier condition) {
		Instant limite = Instant.now().plus(Duration.ofSeconds(5));
		while (!condition.getAsBoolean()) {
			if (Instant.now().isAfter(limite)) {
				throw new AssertionError("Condicao nao atingida em 5 segundos");
			}
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError(ex);
			}
		}
	}

	private HttpEntity<Object> json(String email, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(jwtService.generateToken(email));
		return new HttpEntity<>(body, headers);
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private void limparBase() {
		messageRepository.deleteAll();
		chatRepository.deleteAll();
		userRepository.deleteAll();
	}

}
