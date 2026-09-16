package br.edu.webchat.auth.config;

import br.edu.webchat.auth.filter.JwtAuthenticationFilter;
import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.user.entity.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final SecurityErrorHandler securityErrorHandler;
	private final CorsConfigurationSource corsConfigurationSource;

	public SecurityConfig(JwtService jwtService, UserDetailsService userDetailsService,
			SecurityErrorHandler securityErrorHandler, CorsConfigurationSource corsConfigurationSource) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.securityErrorHandler = securityErrorHandler;
		this.corsConfigurationSource = corsConfigurationSource;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		JwtAuthenticationFilter jwtAuthenticationFilter =
				new JwtAuthenticationFilter(jwtService, userDetailsService);

		return http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
						.requestMatchers(HttpMethod.GET, "/", "/*.html", "/*.txt", "/*.svg", "/favicon.ico",
								"/_next/**", "/chat/**", "/login/**").permitAll()
						.requestMatchers("/ws", "/ws/**").permitAll()
						.requestMatchers(HttpMethod.PUT, "/api/v1/users/me").authenticated()
						.requestMatchers(HttpMethod.PUT, "/api/v1/users/{id}").hasRole(Role.ADMIN.name())
						.requestMatchers(HttpMethod.PATCH, "/api/v1/users/{id}/role").hasRole(Role.ADMIN.name())
						.requestMatchers("/api/v1/users/**").authenticated()
						.requestMatchers("/api/v1/chats/**", "/api/v1/messages/**").authenticated()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(securityErrorHandler)
						.accessDeniedHandler(securityErrorHandler))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

}
