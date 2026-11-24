package br.edu.webchat.game.controller;

import br.edu.webchat.game.dto.SoloRankingResponse;
import br.edu.webchat.game.dto.SoloResultRequest;
import br.edu.webchat.game.dto.SoloResultResponse;
import br.edu.webchat.game.entity.SoloGame;
import br.edu.webchat.game.service.SoloRankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ranking dos jogos solo (campo minado, paciencia), que rodam no navegador. */
@RestController
@RequestMapping("/api/v1/games/solo/{game}")
public class SoloGameController {

	private final SoloRankingService rankingService;

	public SoloGameController(SoloRankingService rankingService) {
		this.rankingService = rankingService;
	}

	@GetMapping("/ranking")
	public SoloRankingResponse ranking(@PathVariable SoloGame game, @RequestParam String mode,
			Authentication authentication) {
		return rankingService.findRanking(authentication.getName(), game, mode);
	}

	@PostMapping("/results")
	@ResponseStatus(HttpStatus.CREATED)
	public SoloResultResponse register(@PathVariable SoloGame game, @Valid @RequestBody SoloResultRequest request,
			Authentication authentication) {
		return rankingService.register(authentication.getName(), game, request);
	}

}
