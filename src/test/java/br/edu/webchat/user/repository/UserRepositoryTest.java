package br.edu.webchat.user.repository;

import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void devePersistirComIdNumericoGeradoETimestamps() {
		User saved = userRepository.save(new User("Alexandre", "alexandre@email.com", "hash"));

		assertThat(saved.getId()).isNotNull().isPositive();
		assertThat(saved.getStatus()).isEqualTo(UserStatus.OFFLINE);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void deveGerarIdsSequenciaisAutomaticamente() {
		Long primeiro = userRepository.save(new User("Alexandre", "alexandre@email.com", "hash")).getId();
		Long segundo = userRepository.save(new User("Joao", "joao@email.com", "hash")).getId();

		assertThat(segundo).isGreaterThan(primeiro);
	}

	@Test
	void deveBuscarPorEmail() {
		userRepository.save(new User("Alexandre", "alexandre@email.com", "hash"));

		assertThat(userRepository.findByEmail("alexandre@email.com"))
				.get()
				.extracting(User::getName)
				.isEqualTo("Alexandre");
		assertThat(userRepository.existsByEmail("alexandre@email.com")).isTrue();
		assertThat(userRepository.existsByEmail("ninguem@email.com")).isFalse();
	}

	@Test
	void bancoDeveRejeitarEmailDuplicado() {
		userRepository.saveAndFlush(new User("Alexandre", "alexandre@email.com", "hash"));

		assertThatThrownBy(() -> userRepository
				.saveAndFlush(new User("Outro", "alexandre@email.com", "hash")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
