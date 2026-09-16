package br.edu.webchat.chat.repository;

import br.edu.webchat.chat.entity.Chat;
import br.edu.webchat.chat.entity.ChatParticipant;
import br.edu.webchat.chat.entity.Message;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MessageRepositoryTest {

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private ChatRepository chatRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	private User alexandre;
	private User maria;
	private Chat comMaria;
	private Chat comJoao;

	@BeforeEach
	void setUp() {
		alexandre = userRepository.save(new User("Alexandre", "alexandre@email.com", "hash"));
		maria = userRepository.save(new User("Maria", "maria@email.com", "hash"));
		User joao = userRepository.save(new User("Joao", "joao@email.com", "hash"));
		comMaria = chatRepository.save(new Chat(alexandre, maria));
		comJoao = chatRepository.save(new Chat(alexandre, joao));
	}

	@Test
	void devePersistirMensagemComData() {
		Message saved = messageRepository.saveAndFlush(new Message(comMaria, alexandre, "oi"));
		entityManager.clear();

		Message reloaded = messageRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getId()).isPositive();
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getChat().getId()).isEqualTo(comMaria.getId());
		assertThat(reloaded.getSender().getId()).isEqualTo(alexandre.getId());
	}

	@Test
	void historicoDeveTrazerAsMaisRecentesPrimeiroEPaginarPorCursor() {
		List<Long> ids = enviar(comMaria, alexandre, 5);
		enviar(comJoao, alexandre, 2);

		List<Message> latest = messageRepository.findLatest(comMaria.getId(), PageRequest.ofSize(2));
		assertThat(latest).extracting(Message::getId).containsExactly(ids.get(4), ids.get(3));

		List<Message> before = messageRepository.findBefore(comMaria.getId(), ids.get(3), PageRequest.ofSize(2));
		assertThat(before).extracting(Message::getId).containsExactly(ids.get(2), ids.get(1));

		List<Message> last = messageRepository.findBefore(comMaria.getId(), ids.get(1), PageRequest.ofSize(2));
		assertThat(last).extracting(Message::getId).containsExactly(ids.get(0));
	}

	@Test
	void deveTrazerAUltimaMensagemDeCadaConversa() {
		Long ultimaComMaria = enviar(comMaria, maria, 3).get(2);
		Long ultimaComJoao = enviar(comJoao, alexandre, 2).get(1);
		entityManager.clear();

		assertThat(messageRepository.findLastMessagesOfChats(List.of(comMaria.getId(), comJoao.getId())))
				.extracting(Message::getId)
				.containsExactlyInAnyOrder(ultimaComMaria, ultimaComJoao);
	}

	@Test
	void naoLidasContamSomenteMensagensDoOutroParticipante() {
		enviar(comMaria, maria, 3);
		enviar(comMaria, alexandre, 2);
		enviar(comJoao, alexandre, 4);

		List<MessageRepository.UnreadCount> paraAlexandre =
				messageRepository.countUnreadByChat(List.of(comMaria.getId(), comJoao.getId()), alexandre.getId());

		assertThat(paraAlexandre).hasSize(1);
		assertThat(paraAlexandre.get(0).getChatId()).isEqualTo(comMaria.getId());
		assertThat(paraAlexandre.get(0).getTotal()).isEqualTo(3);
	}

	@Test
	void marcadorDeLeituraDeUmParticipanteNaoAfetaOOutro() {
		List<Long> daMaria = enviar(comMaria, maria, 3);
		enviar(comMaria, alexandre, 2);
		enviar(comJoao, alexandre, 1);

		// Alexandre le tudo: seu marcador vai para a ultima mensagem da conversa
		Long ultima = messageRepository.findLastMessageId(comMaria.getId());
		assertThat(messageRepository.countUnread(comMaria.getId(), alexandre.getId(), 0, ultima)).isEqualTo(3);
		marcarLido(comMaria, alexandre, ultima);
		entityManager.clear();

		assertThat(messageRepository.countUnreadByChat(List.of(comMaria.getId()), alexandre.getId())).isEmpty();
		assertThat(messageRepository.countUnreadByChat(List.of(comMaria.getId()), maria.getId()))
				.singleElement()
				.satisfies(unread -> assertThat(unread.getTotal()).isEqualTo(2));

		// leitura parcial da Maria: so as anteriores ao marcador saem da conta
		marcarLido(comMaria, maria, daMaria.get(2));
		entityManager.clear();

		assertThat(messageRepository.countUnreadByChat(List.of(comMaria.getId()), maria.getId()))
				.singleElement()
				.satisfies(unread -> assertThat(unread.getTotal()).isEqualTo(2));
	}

	@Test
	void ultimaMensagemDaConversaEUsadaComoMarcador() {
		assertThat(messageRepository.findLastMessageId(comMaria.getId())).isNull();

		List<Long> ids = enviar(comMaria, alexandre, 3);

		assertThat(messageRepository.findLastMessageId(comMaria.getId())).isEqualTo(ids.get(2));
	}

	private void marcarLido(Chat chat, User user, Long messageId) {
		ChatParticipant participant = chat.participantOf(user.getId()).orElseThrow();
		participant.setLastReadMessage(entityManager.getEntityManager().getReference(Message.class, messageId));
		entityManager.flush();
	}

	private List<Long> enviar(Chat chat, User sender, int quantidade) {
		return java.util.stream.IntStream.rangeClosed(1, quantidade)
				.mapToObj(i -> messageRepository.saveAndFlush(new Message(chat, sender, "mensagem " + i)).getId())
				.toList();
	}

}
