package br.edu.webchat.chat.websocket;

import br.edu.webchat.shared.exception.WebSocketExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	public static final String ENDPOINT = "/ws";
	public static final String APP_PREFIX = "/app";
	public static final String USER_PREFIX = "/user";
	public static final String PRESENCE_TOPIC = "/topic/presence";
	public static final String PROFILES_TOPIC = "/topic/profiles";
	public static final String USERS_TOPIC = "/topic/users";
	public static final String MESSAGES_QUEUE = "/queue/messages";
	public static final String READ_QUEUE = "/queue/read";
	public static final String MESSAGE_UPDATES_QUEUE = "/queue/message-updates";
	public static final String CHATS_QUEUE = "/queue/chats";
	public static final String ERRORS_QUEUE = WebSocketExceptionHandler.ERRORS_QUEUE;
	public static final String CALLS_QUEUE = "/queue/calls";
	public static final String CALL_SIGNAL_QUEUE = "/queue/call-signal";

	private final StompAuthInterceptor stompAuthInterceptor;
	private final String[] allowedOrigins;

	public WebSocketConfig(StompAuthInterceptor stompAuthInterceptor,
			@Value("${app.websocket.allowed-origins}") String[] allowedOrigins) {
		this.stompAuthInterceptor = stompAuthInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(allowedOrigins);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes(APP_PREFIX);
		registry.setUserDestinationPrefix(USER_PREFIX);
		registry.enableSimpleBroker("/topic", "/queue");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthInterceptor);
	}

}
