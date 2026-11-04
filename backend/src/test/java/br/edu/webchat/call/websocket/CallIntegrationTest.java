package br.edu.webchat.call.websocket;

import br.edu.webchat.call.dto.CallEndReason;
import br.edu.webchat.call.dto.CallEventResponse;
import br.edu.webchat.call.dto.CallEventType;
import br.edu.webchat.call.dto.CallSignalResponse;
import br.edu.webchat.call.dto.SignalType;
import br.edu.webchat.shared.exception.ApiError;
import br.edu.webchat.support.StompIntegrationTest;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Sinalizacao das chamadas de voz com servidor, WebSocket, STOMP, seguranca e banco reais. */
class CallIntegrationTest extends StompIntegrationTest {

	private static final String ALEXANDRE = "alexandre@email.com";
	private static final String MARIA = "maria@email.com";
	private static final String JOAO = "joao@email.com";
	private static final String CALLS = "/user/queue/calls";
	private static final String SIGNALS = "/user/queue/call-signal";
	private static final String ERRORS = "/user/queue/errors";
	private static final Map<String, Object> SEM_CORPO = Map.of();

	@Autowired
	private UserService userService;

	private Long alexandreId;
	private Long mariaId;
	private Long joaoId;
	private Long chatId;

	@BeforeEach
	void setUp() {
		limparBase();
		alexandreId = userService.create(new CreateUserRequest("Alexandre", ALEXANDRE, "123456")).id();
		mariaId = userService.create(new CreateUserRequest("Maria", MARIA, "123456")).id();
		joaoId = userService.create(new CreateUserRequest("Joao", JOAO, "123456")).id();
		chatId = conversaDireta(ALEXANDRE, mariaId);
	}

	@Test
	void chamadaCompletaTocaAtendeTrocaSinaisEDesliga() throws Exception {
		StompSession maria = conectar(MARIA);
		BlockingQueue<CallEventResponse> eventosMaria = subscribe(maria, MARIA, CALLS, CallEventResponse.class);
		BlockingQueue<CallSignalResponse> sinaisMaria = subscribe(maria, MARIA, SIGNALS, CallSignalResponse.class);
		StompSession alexandre = conectar(ALEXANDRE);
		BlockingQueue<CallEventResponse> eventosAlexandre = subscribe(alexandre, ALEXANDRE, CALLS, CallEventResponse.class);
		BlockingQueue<CallSignalResponse> sinaisAlexandre = subscribe(alexandre, ALEXANDRE, SIGNALS, CallSignalResponse.class);
		aguardarOnline(mariaId);

		alexandre.send(destino("start"), SEM_CORPO);
		assertThat(eventosMaria.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.RINGING, null));
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.RINGING, null));

		maria.send(destino("accept"), SEM_CORPO);
		assertThat(eventosMaria.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.ACCEPTED, null));
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.ACCEPTED, null));

		// O servidor so repassa o SDP: quem ligou manda a oferta, quem atendeu responde.
		alexandre.send(destino("signal"), Map.of("type", "OFFER", "data", Map.of("type", "offer", "sdp", "v=0 oferta")));
		CallSignalResponse oferta = sinaisMaria.poll(5, TimeUnit.SECONDS);
		assertThat(oferta).isNotNull();
		assertThat(oferta.fromId()).isEqualTo(alexandreId);
		assertThat(oferta.type()).isEqualTo(SignalType.OFFER);
		assertThat(oferta.data().get("sdp").asText()).isEqualTo("v=0 oferta");

		maria.send(destino("signal"), Map.of("type", "ANSWER", "data", Map.of("type", "answer", "sdp", "v=0 resposta")));
		CallSignalResponse resposta = sinaisAlexandre.poll(5, TimeUnit.SECONDS);
		assertThat(resposta).isNotNull();
		assertThat(resposta.fromId()).isEqualTo(mariaId);
		assertThat(resposta.data().get("sdp").asText()).isEqualTo("v=0 resposta");

		maria.send(destino("end"), SEM_CORPO);
		assertThat(eventosMaria.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.ENDED, CallEndReason.HUNG_UP));
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.ENDED, CallEndReason.HUNG_UP));
	}

	@Test
	void quedaDaConexaoDeQuemAtendeuEncerraAChamada() throws Exception {
		StompSession maria = conectar(MARIA);
		BlockingQueue<CallEventResponse> eventosMaria = subscribe(maria, MARIA, CALLS, CallEventResponse.class);
		StompSession alexandre = conectar(ALEXANDRE);
		BlockingQueue<CallEventResponse> eventosAlexandre = subscribe(alexandre, ALEXANDRE, CALLS, CallEventResponse.class);
		aguardarOnline(mariaId);

		alexandre.send(destino("start"), SEM_CORPO);
		assertThat(eventosMaria.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.RINGING, null));
		maria.send(destino("accept"), SEM_CORPO);
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.RINGING, null));
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.ACCEPTED, null));

		maria.disconnect();

		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS))
				.isEqualTo(evento(CallEventType.ENDED, CallEndReason.DISCONNECTED));
	}

	@Test
	void ligarParaQuemEstaOfflineOuEmOutraChamadaAvisaSoQuemLigou() throws Exception {
		StompSession alexandre = conectar(ALEXANDRE);
		BlockingQueue<CallEventResponse> eventosAlexandre = subscribe(alexandre, ALEXANDRE, CALLS, CallEventResponse.class);

		// Maria ainda nao conectou: esta offline.
		alexandre.send(destino("start"), SEM_CORPO);
		assertThat(eventosAlexandre.poll(5, TimeUnit.SECONDS))
				.isEqualTo(evento(CallEventType.ENDED, CallEndReason.UNAVAILABLE));

		StompSession maria = conectar(MARIA);
		BlockingQueue<CallEventResponse> eventosMaria = subscribe(maria, MARIA, CALLS, CallEventResponse.class);
		aguardarOnline(mariaId);
		alexandre.send(destino("start"), SEM_CORPO);
		assertThat(eventosMaria.poll(5, TimeUnit.SECONDS)).isEqualTo(evento(CallEventType.RINGING, null));

		// Joao liga para Maria, que ja esta tocando com Alexandre.
		Long conversaJoaoMaria = conversaDireta(JOAO, mariaId);
		StompSession joao = conectar(JOAO);
		BlockingQueue<CallEventResponse> eventosJoao = subscribe(joao, JOAO, CALLS, CallEventResponse.class);
		joao.send("/app/calls/" + conversaJoaoMaria + "/start", SEM_CORPO);

		assertThat(eventosJoao.poll(5, TimeUnit.SECONDS)).isEqualTo(
				new CallEventResponse(CallEventType.ENDED, conversaJoaoMaria, joaoId, mariaId, CallEndReason.BUSY));
		assertThat(eventosMaria.poll(500, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void errosDaChamadaVoltamSoParaQuemEnviou() throws Exception {
		StompSession joao = conectar(JOAO);
		BlockingQueue<ApiError> errosJoao = subscribe(joao, JOAO, ERRORS, ApiError.class);
		StompSession maria = conectar(MARIA);
		BlockingQueue<CallEventResponse> eventosMaria = subscribe(maria, MARIA, CALLS, CallEventResponse.class);
		BlockingQueue<ApiError> errosMaria = subscribe(maria, MARIA, ERRORS, ApiError.class);

		// Joao nao participa da conversa de Alexandre e Maria.
		joao.send(destino("start"), SEM_CORPO);
		ApiError proibido = errosJoao.poll(5, TimeUnit.SECONDS);
		assertThat(proibido).isNotNull();
		assertThat(proibido.status()).isEqualTo(403);
		assertThat(eventosMaria.poll(500, TimeUnit.MILLISECONDS)).isNull();

		// Sem chamada em andamento nao ha o que atender.
		maria.send(destino("accept"), SEM_CORPO);
		assertThat(errosMaria.poll(5, TimeUnit.SECONDS).status()).isEqualTo(404);

		// Tipo de sinal desconhecido e erro de quem enviou, nao do servidor.
		maria.send(destino("signal"), Map.of("type", "BOGUS", "data", Map.of()));
		assertThat(errosMaria.poll(5, TimeUnit.SECONDS).status()).isEqualTo(400);
		assertThat(errosJoao.poll(500, TimeUnit.MILLISECONDS)).isNull();
	}

	private CallEventResponse evento(CallEventType type, CallEndReason reason) {
		return new CallEventResponse(type, chatId, alexandreId, mariaId, reason);
	}

	private String destino(String acao) {
		return "/app/calls/" + chatId + "/" + acao;
	}

	private Long conversaDireta(String email, Long participantId) {
		Map<?, ?> chat = rest.exchange(url("/api/v1/chats"), HttpMethod.POST,
				json(email, Map.of("participantId", participantId)), Map.class).getBody();
		return ((Number) chat.get("id")).longValue();
	}

	private void aguardarOnline(Long userId) {
		aguardar(() -> userRepository.findById(userId).orElseThrow().getStatus() == UserStatus.ONLINE);
	}

}
