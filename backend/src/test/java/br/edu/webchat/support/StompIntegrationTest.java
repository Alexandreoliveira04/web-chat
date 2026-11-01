package br.edu.webchat.support;

import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.chat.repository.ChatRepository;
import br.edu.webchat.chat.repository.MessageRepository;
import br.edu.webchat.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Base dos testes de tempo real: sobe o servidor numa porta aleatoria e oferece um
 * cliente STOMP de verdade, autenticado com JWT, alem de REST para preparar os dados.
 * As sessoes abertas sao desconectadas e a base e limpa depois de cada teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class StompIntegrationTest {

	@LocalServerPort
	protected int port;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected JwtService jwtService;

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected ChatRepository chatRepository;

	@Autowired
	protected MessageRepository messageRepository;

	@Autowired
	protected SimpUserRegistry userRegistry;

	protected final RestTemplate rest = new RestTemplate(new JdkClientHttpRequestFactory());

	private final List<StompSession> sessions = new ArrayList<>();
	private WebSocketStompClient stompClient;

	@BeforeEach
	void setUpStompClient() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setObjectMapper(objectMapper);
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(converter);
	}

	@AfterEach
	void disconnectAndCleanUp() {
		// Desconectar tambem encerra o que ficou aberto em memoria (presenca, chamadas).
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		sessions.clear();
		aguardar(() -> userRegistry.getUserCount() == 0);
		limparBase();
	}

	protected StompSession conectar(String email) throws Exception {
		return connect("Bearer " + jwtService.generateToken(email));
	}

	protected StompSession connect(String authorization) throws Exception {
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

	/** Assina o destino e so devolve a fila depois que o servidor registrou a assinatura. */
	protected <T> BlockingQueue<T> subscribe(StompSession session, String email, String destination, Class<T> type) {
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

	protected static void aguardar(BooleanSupplier condition) {
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

	protected HttpEntity<Object> json(String email, Object body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(jwtService.generateToken(email));
		return new HttpEntity<>(body, headers);
	}

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}

	protected void limparBase() {
		messageRepository.deleteAll();
		chatRepository.deleteAll();
		userRepository.deleteAll();
	}

}
