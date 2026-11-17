package br.edu.webchat.game.service;

import br.edu.webchat.game.dto.PlayerResponse;
import br.edu.webchat.game.dto.SoloRankingResponse;
import br.edu.webchat.game.dto.SoloResultRequest;
import br.edu.webchat.game.dto.SoloResultResponse;
import br.edu.webchat.game.entity.SoloGame;
import br.edu.webchat.game.entity.SoloResult;
import br.edu.webchat.game.repository.SoloResultRepository;
import br.edu.webchat.shared.exception.BadRequestException;
import br.edu.webchat.shared.exception.NotFoundException;
import br.edu.webchat.user.entity.User;
import br.edu.webchat.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ranking dos jogos solo. O jogo roda no navegador e o servidor confia no tempo enviado,
 * dentro de limites plausiveis (ver {@link SoloResultRequest}): validar a partida em si
 * exigiria jogar no servidor, o que nao compensa para um ranking entre colegas.
 */
@Service
public class SoloRankingService {

	static final int TOP_SIZE = 10;

	private final SoloResultRepository resultRepository;
	private final UserRepository userRepository;

	public SoloRankingService(SoloResultRepository resultRepository, UserRepository userRepository) {
		this.resultRepository = resultRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public SoloResultResponse register(String authenticatedEmail, SoloGame game, SoloResultRequest request) {
		User me = findUser(authenticatedEmail);
		String mode = requireMode(game, request.mode());

		Long previousBest = resultRepository.findBestDuration(me.getId(), game, mode);
		resultRepository.save(new SoloResult(me, game, mode, request.durationMs(), request.moves()));

		boolean personalBest = previousBest == null || request.durationMs() < previousBest;
		return new SoloResultResponse(request.durationMs(), personalBest, ranking(me, game, mode));
	}

	@Transactional(readOnly = true)
	public SoloRankingResponse findRanking(String authenticatedEmail, SoloGame game, String mode) {
		return ranking(findUser(authenticatedEmail), game, requireMode(game, mode));
	}

	private SoloRankingResponse ranking(User me, SoloGame game, String mode) {
		List<SoloResultRepository.BestTime> rows = resultRepository.findBestTimes(game, mode);

		List<SoloRankingResponse.Entry> top = new ArrayList<>();
		SoloRankingResponse.Entry mine = null;
		for (int i = 0; i < rows.size(); i++) {
			SoloRankingResponse.Entry entry = toEntry(i + 1, rows.get(i));
			if (i < TOP_SIZE) {
				top.add(entry);
			}
			if (entry.player().id().equals(me.getId())) {
				mine = entry;
			}
		}

		return new SoloRankingResponse(game, mode, top, mine);
	}

	private static SoloRankingResponse.Entry toEntry(int position, SoloResultRepository.BestTime row) {
		return new SoloRankingResponse.Entry(position,
				new PlayerResponse(row.getUserId(), row.getName(), row.getAvatarUrl()),
				row.getBestDurationMs(), row.getWins());
	}

	private static String requireMode(SoloGame game, String mode) {
		String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
		if (!game.modes().contains(normalized)) {
			throw new BadRequestException("Modo invalido para " + game + "; use um de " + game.modes());
		}
		return normalized;
	}

	private User findUser(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + email));
	}

}
