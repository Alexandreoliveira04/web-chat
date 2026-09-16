package br.edu.webchat.auth.service;

import br.edu.webchat.auth.dto.LoginRequest;
import br.edu.webchat.auth.dto.LoginResponse;
import br.edu.webchat.auth.jwt.JwtService;
import br.edu.webchat.shared.exception.UnauthorizedException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final JwtService jwtService =
			new JwtService("segredo-de-teste-com-tamanho-suficiente-para-hs256", 3600);

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, passwordEncoder, jwtService);
	}

	@Test
	void deveAutenticarComCredenciaisValidas() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(usuario()));

		LoginResponse response = authService.login(
				new LoginRequest("alexandre@email.com", "123456"));

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600);
		assertThat(jwtService.extractEmail(response.token())).contains("alexandre@email.com");
	}

	@Test
	void deveNormalizarOEmailAntesDeBuscar() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(usuario()));

		assertThat(authService.login(new LoginRequest("ALEXANDRE@Email.COM", "123456")).token())
				.isNotBlank();
	}

	@Test
	void deveRecusarEmailInexistente() {
		when(userRepository.findByEmail("ninguem@email.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem@email.com", "123456")))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void deveRecusarSenhaIncorreta() {
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(usuario()));

		assertThatThrownBy(() -> authService.login(
				new LoginRequest("alexandre@email.com", "senha-errada")))
				.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void mensagemDeveSerIgualParaEmailInexistenteESenhaIncorreta() {
		when(userRepository.findByEmail("ninguem@email.com")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(usuario()));

		String semUsuario = capturarMensagem("ninguem@email.com", "123456");
		String senhaErrada = capturarMensagem("alexandre@email.com", "errada");

		// nao pode ser possivel descobrir quais e-mails existem pela resposta
		assertThat(semUsuario).isEqualTo(senhaErrada);
	}

	private String capturarMensagem(String email, String senha) {
		try {
			authService.login(new LoginRequest(email, senha));
			throw new AssertionError("deveria ter falhado");
		}
		catch (UnauthorizedException ex) {
			return ex.getMessage();
		}
	}

	private User usuario() {
		return new User("Alexandre Oliveira", "alexandre@email.com", passwordEncoder.encode("123456"));
	}

}
