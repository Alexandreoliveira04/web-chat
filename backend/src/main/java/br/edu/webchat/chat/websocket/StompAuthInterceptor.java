package br.edu.webchat.chat.websocket;

import br.edu.webchat.auth.jwt.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public StompAuthInterceptor(JwtService jwtService, UserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		switch (accessor.getCommand()) {
			case CONNECT -> accessor.setUser(authenticate(accessor));
			case SUBSCRIBE -> requireAllowedDestination(accessor, isAllowedSubscription(accessor.getDestination()));
			case SEND -> requireAllowedDestination(accessor, isAllowedSend(accessor.getDestination()));
			default -> { }
		}

		return message;
	}

	private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
		String email = extractToken(accessor)
				.flatMap(jwtService::extractEmail)
				.orElseThrow(() -> new MessageDeliveryException("Autenticacao necessaria"));

		try {
			UserDetails user = userDetailsService.loadUserByUsername(email);
			return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
		}
		catch (UsernameNotFoundException ex) {
			throw new MessageDeliveryException("Autenticacao necessaria");
		}
	}

	private static void requireAllowedDestination(StompHeaderAccessor accessor, boolean allowed) {
		if (accessor.getUser() == null) {
			throw new MessageDeliveryException("Autenticacao necessaria");
		}
		if (!allowed) {
			throw new MessageDeliveryException("Destino nao permitido: " + accessor.getDestination());
		}
	}

	private static boolean isAllowedSubscription(String destination) {
		return destination != null && (destination.equals(WebSocketConfig.PRESENCE_TOPIC)
				|| destination.equals(WebSocketConfig.USER_PREFIX + WebSocketConfig.MESSAGES_QUEUE)
				|| destination.equals(WebSocketConfig.USER_PREFIX + WebSocketConfig.READ_QUEUE)
				|| destination.equals(WebSocketConfig.USER_PREFIX + WebSocketConfig.MESSAGE_UPDATES_QUEUE)
				|| destination.equals(WebSocketConfig.USER_PREFIX + WebSocketConfig.CHATS_QUEUE)
				|| destination.equals(WebSocketConfig.USER_PREFIX + WebSocketConfig.ERRORS_QUEUE));
	}

	private static boolean isAllowedSend(String destination) {
		return destination != null && destination.startsWith(WebSocketConfig.APP_PREFIX + "/");
	}

	private static Optional<String> extractToken(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader("Authorization");

		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return Optional.empty();
		}

		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? Optional.empty() : Optional.of(token);
	}

}
