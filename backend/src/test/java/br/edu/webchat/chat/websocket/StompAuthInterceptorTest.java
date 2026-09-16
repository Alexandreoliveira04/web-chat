package br.edu.webchat.chat.websocket;

import br.edu.webchat.auth.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

	private static final String EMAIL = "alexandre@email.com";

	@Mock
	private UserDetailsService userDetailsService;

	@Mock
	private MessageChannel channel;

	private final JwtService jwtService = new JwtService("segredo-de-teste-com-tamanho-suficiente-para-hs256", 3600);

	private StompAuthInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new StompAuthInterceptor(jwtService, userDetailsService);
	}

	@Test
	void connectComTokenValidoDeveAutenticarASessao() {
		when(userDetailsService.loadUserByUsername(EMAIL))
				.thenReturn(User.withUsername(EMAIL).password("hash").roles("USER").build());

		Message<?> message = frame(StompCommand.CONNECT, null, "Bearer " + jwtService.generateToken(EMAIL), null);
		interceptor.preSend(message, channel);

		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
		assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
		assertThat(accessor.getUser().getName()).isEqualTo(EMAIL);
	}

	@Test
	void connectSemTokenDeveSerRecusado() {
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
				.isInstanceOf(MessageDeliveryException.class);
	}

	@Test
	void connectComTokenInvalidoOuSemBearerDeveSerRecusado() {
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer invalido", null), channel))
				.isInstanceOf(MessageDeliveryException.class);
		assertThatThrownBy(() -> interceptor.preSend(
				frame(StompCommand.CONNECT, null, jwtService.generateToken(EMAIL), null), channel))
				.isInstanceOf(MessageDeliveryException.class);
	}

	@Test
	void connectDeUsuarioRemovidoDeveSerRecusado() {
		when(userDetailsService.loadUserByUsername(EMAIL)).thenThrow(new UsernameNotFoundException("removido"));

		assertThatThrownBy(() -> interceptor.preSend(
				frame(StompCommand.CONNECT, null, "Bearer " + jwtService.generateToken(EMAIL), null), channel))
				.isInstanceOf(MessageDeliveryException.class);
	}

	@Test
	void subscribeSomenteNosDestinosPermitidos() {
		UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

		for (String allowed : List.of("/user/queue/messages", "/user/queue/read", "/user/queue/errors", "/topic/presence")) {
			Message<?> message = frame(StompCommand.SUBSCRIBE, allowed, null, user);
			assertThat(interceptor.preSend(message, channel)).isSameAs(message);
		}

		for (String denied : List.of("/topic/chats/1", "/queue/messages-user123", "/user/queue/outra", "/app/x")) {
			assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, denied, null, user), channel))
					.isInstanceOf(MessageDeliveryException.class);
		}
	}

	@Test
	void sendSomenteParaAplicacaoEComSessaoAutenticada() {
		UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

		Message<?> allowed = frame(StompCommand.SEND, "/app/chats/1/messages", null, user);
		assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);

		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/presence", null, user), channel))
				.isInstanceOf(MessageDeliveryException.class);
		assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chats/1/messages", null, null), channel))
				.isInstanceOf(MessageDeliveryException.class);
	}

	private static Message<?> frame(StompCommand command, String destination, String authorization,
			UsernamePasswordAuthenticationToken user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		if (destination != null) {
			accessor.setDestination(destination);
		}
		if (authorization != null) {
			accessor.addNativeHeader("Authorization", authorization);
		}
		if (user != null) {
			accessor.setUser(user);
		}
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

}
