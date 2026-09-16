package br.edu.webchat.chat.repository;

import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRepositoryTest {

	@Autowired
	private ChatRepository chatRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	private User alexandre;
	private User maria;
	private User joao;

	@BeforeEach
	void setUp() {
		alexandre = userRepository.save(new User("Alexandre", "alexandre@email.com", "hash"));
		maria = userRepository.save(new User("Maria", "maria@email.com", "hash"));
		joao = userRepository.save(new User("Joao", "joao@email.com", "hash"));
	}

	@Test
	void devePersistirConversaComDoisParticipantesETimestamps() {
		Chat chat = chatRepository.saveAndFlush(new Chat(alexandre, maria));
		entityManager.clear();

		Chat recarregado = chatRepository.findWithParticipantsById(chat.getId()).orElseThrow();

		assertThat(recarregado.getId()).isPositive();
		assertThat(recarregado.getDirectKey()).isEqualTo(alexandre.getId() + ":" + maria.getId());
		assertThat(recarregado.getCreatedAt()).isNotNull();
		assertThat(recarregado.getUpdatedAt()).isNotNull();
		assertThat(Hibernate.isInitialized(recarregado.getParticipants())).isTrue();
		assertThat(recarregado.users())
				.extracting(User::getEmail)
				.containsExactlyInAnyOrder("alexandre@email.com", "maria@email.com");
		assertThat(recarregado.getParticipants())
				.allSatisfy(participant -> {
					assertThat(participant.getJoinedAt()).isNotNull();
					assertThat(participant.getLastReadMessageId()).isNull();
				});
	}

	@Test
	void deveEncontrarConversaPelaChaveDoParComParticipantesCarregados() {
		Chat chat = chatRepository.saveAndFlush(new Chat(maria, alexandre));
		chatRepository.saveAndFlush(new Chat(alexandre, joao));
		entityManager.clear();

		Chat encontrada = chatRepository.findByDirectKey(Chat.directKeyOf(alexandre.getId(), maria.getId()))
				.orElseThrow();

		assertThat(encontrada.getId()).isEqualTo(chat.getId());
		assertThat(Hibernate.isInitialized(encontrada.getParticipants())).isTrue();
		assertThat(chatRepository.findByDirectKey(Chat.directKeyOf(maria.getId(), joao.getId()))).isEmpty();
	}

	@Test
	void bancoDeveRejeitarSegundaConversaParaOMesmoPar() {
		chatRepository.saveAndFlush(new Chat(alexandre, maria));

		assertThatThrownBy(() -> chatRepository.saveAndFlush(new Chat(maria, alexandre)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deveListarSomenteAsConversasDoUsuarioComParticipantesCarregados() {
		Chat antiga = chatRepository.saveAndFlush(new Chat(alexandre, maria));
		Chat recente = chatRepository.saveAndFlush(new Chat(alexandre, joao));
		chatRepository.saveAndFlush(new Chat(maria, joao));
		entityManager.clear();

		List<Chat> chats = chatRepository.findAllByParticipantId(alexandre.getId());

		assertThat(chats).extracting(Chat::getId).containsExactly(recente.getId(), antiga.getId());
		assertThat(chats).allSatisfy(chat -> {
			assertThat(Hibernate.isInitialized(chat.getParticipants())).isTrue();
			assertThat(chat.getParticipants()).hasSize(2);
		});
	}

	@Test
	void usuarioSemConversasDeveReceberListaVazia() {
		chatRepository.saveAndFlush(new Chat(alexandre, maria));
		entityManager.clear();

		assertThat(chatRepository.findAllByParticipantId(joao.getId())).isEmpty();
	}

}
