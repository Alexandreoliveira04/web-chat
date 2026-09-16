package br.edu.webchat.user.service;

import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Exercita o fluxo real do service contra o JPA, sem mocks. */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceIntegrationTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void limparBase() {
		userRepository.deleteAll();
	}

	@Test
	void deveGerarIdNumericoEPersistirSomenteOHashDaSenha() {
		UserResponse response = userService.create(
				new CreateUserRequest("Alexandre Oliveira", "alexandre@email.com", "123456"));

		assertThat(response.id()).isNotNull().isPositive();
		assertThat(response.status()).isEqualTo(UserStatus.OFFLINE);

		User persistido = userRepository.findByEmail("alexandre@email.com").orElseThrow();
		assertThat(persistido.getPassword())
				.isNotEqualTo("123456")
				.startsWith("$2");
		assertThat(passwordEncoder.matches("123456", persistido.getPassword())).isTrue();
	}

	@Test
	void deveRejeitarEmailJaCadastrado() {
		userService.create(new CreateUserRequest("Alexandre", "alexandre@email.com", "123456"));

		assertThatThrownBy(() -> userService.create(
				new CreateUserRequest("Impostor", "ALEXANDRE@EMAIL.COM", "654321")))
				.isInstanceOf(ConflictException.class);

		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void atualizacaoDeveRefletirONovoUpdatedAtNaResposta() throws InterruptedException {
		UserResponse criado = userService.create(
				new CreateUserRequest("Alexandre", "alexandre@email.com", "123456"));

		Thread.sleep(10); // garante um instante distinto do createdAt

		UserResponse atualizado = userService.update(criado.id(), new UpdateUserRequest("Alexandre Silva"));

		assertThat(atualizado.name()).isEqualTo("Alexandre Silva");
		assertThat(atualizado.updatedAt()).isAfter(criado.updatedAt());
		// o banco guarda timestamptz com precisao de microssegundos, entao os
		// nanossegundos do Instant sao truncados ao recarregar - dai a tolerancia
		assertThat(atualizado.createdAt()).isCloseTo(criado.createdAt(), within(1, ChronoUnit.MILLIS));
		assertThat(atualizado.email()).isEqualTo(criado.email());
	}

}
