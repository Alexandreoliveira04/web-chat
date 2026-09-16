package br.edu.webchat.user.service;

import br.edu.webchat.shared.exception.ConflictException;
import br.edu.webchat.shared.exception.ForbiddenException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.dto.CreateUserRequest;
import br.edu.webchat.user.dto.UpdateUserRequest;
import br.edu.webchat.user.dto.UserResponse;
import br.edu.webchat.user.entity.Role;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;
import br.edu.webchat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	private UserService userService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	void deveCriarUsuarioComStatusOfflineESenhaCodificada() {
		when(userRepository.existsByEmail("alexandre@email.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.create(
				new CreateUserRequest("Alexandre Oliveira", "alexandre@email.com", "123456"));

		assertThat(response.name()).isEqualTo("Alexandre Oliveira");
		assertThat(response.email()).isEqualTo("alexandre@email.com");
		assertThat(response.status()).isEqualTo(UserStatus.OFFLINE);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().getPassword())
				.isNotEqualTo("123456")
				.matches(hash -> passwordEncoder.matches("123456", hash));
	}

	@Test
	void deveNormalizarEmailAoCriar() {
		when(userRepository.existsByEmail("alexandre@email.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.create(
				new CreateUserRequest("Alexandre", "  Alexandre@Email.COM  ", "123456"));

		assertThat(response.email()).isEqualTo("alexandre@email.com");
	}

	@Test
	void naoDevePermitirEmailDuplicado() {
		when(userRepository.existsByEmail("alexandre@email.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.create(
				new CreateUserRequest("Alexandre", "alexandre@email.com", "123456")))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("alexandre@email.com");

		verify(userRepository, never()).save(any());
	}

	@Test
	void deveBuscarUsuarioExistentePorId() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(usuario()));

		UserResponse response = userService.findById(1L);

		assertThat(response.name()).isEqualTo("Alexandre Oliveira");
		assertThat(response.email()).isEqualTo("alexandre@email.com");
	}

	@Test
	void deveFalharAoBuscarUsuarioInexistente() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.findById(99L))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("99");
	}

	@Test
	void deveListarUsuarios() {
		when(userRepository.findAll()).thenReturn(List.of(usuario()));

		assertThat(userService.findAll())
				.extracting(UserResponse::email)
				.containsExactly("alexandre@email.com");
	}

	@Test
	void deveAtualizarApenasONome() {
		User user = usuario();
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.update(1L, new UpdateUserRequest("Alexandre Oliveira Silva"));

		assertThat(response.name()).isEqualTo("Alexandre Oliveira Silva");
		assertThat(response.email()).isEqualTo("alexandre@email.com");
		assertThat(user.getPassword()).isEqualTo("hash-original");
		assertThat(user.getStatus()).isEqualTo(UserStatus.OFFLINE);
	}

	@Test
	void deveFalharAoAtualizarUsuarioInexistente() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.update(99L, new UpdateUserRequest("Novo Nome")))
				.isInstanceOf(NotFoundException.class);
	}

	// ---------- /me ----------

	@Test
	void updateMeDeveAtualizarOUsuarioDoEmailAutenticado() {
		User user = usuario();
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.updateMe("alexandre@email.com", new UpdateUserRequest("  Alexandre Silva  "));

		assertThat(response.name()).isEqualTo("Alexandre Silva");
		assertThat(user.getRole()).isEqualTo(Role.USER);
	}

	// ---------- papeis ----------

	@Test
	void cadastroDeveCriarSempreComPapelUser() {
		when(userRepository.existsByEmail("alexandre@email.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.create(
				new CreateUserRequest("Alexandre Oliveira", "alexandre@email.com", "123456"));

		assertThat(response.role()).isEqualTo(Role.USER);
	}

	@Test
	void adminDeveAlterarOPapelDeOutroUsuario() {
		User user = usuario();
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.changeRole(1L, Role.ADMIN, "admin@email.com");

		assertThat(response.role()).isEqualTo(Role.ADMIN);
	}

	@Test
	void naoDevePermitirAlterarOProprioPapel() {
		User admin = new User("Admin", "admin@email.com", "hash", Role.ADMIN);
		when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

		assertThatThrownBy(() -> userService.changeRole(1L, Role.USER, " ADMIN@email.com "))
				.isInstanceOf(ForbiddenException.class);

		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void ensureAdminDeveCriarAdministradorQuandoNaoExiste() {
		when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.ensureAdmin("Administrador", "Admin@Email.com", "admin123");

		assertThat(response.role()).isEqualTo(Role.ADMIN);
		assertThat(response.email()).isEqualTo("admin@email.com");

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		assertThat(passwordEncoder.matches("admin123", saved.getValue().getPassword())).isTrue();
	}

	@Test
	void ensureAdminDevePromoverUsuarioExistenteSemTrocarASenha() {
		User user = usuario();
		when(userRepository.findByEmail("alexandre@email.com")).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.ensureAdmin("Administrador", "alexandre@email.com", "outra-senha");

		assertThat(user.getRole()).isEqualTo(Role.ADMIN);
		assertThat(user.getPassword()).isEqualTo("hash-original");
		assertThat(user.getName()).isEqualTo("Alexandre Oliveira");
	}

	@Test
	void ensureAdminDeveExigirSenhaAoCriar() {
		when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.ensureAdmin("Administrador", "admin@email.com", ""))
				.isInstanceOf(IllegalStateException.class);

		verify(userRepository, never()).saveAndFlush(any());
	}

	private static User usuario() {
		return new User("Alexandre Oliveira", "alexandre@email.com", "hash-original");
	}

}
