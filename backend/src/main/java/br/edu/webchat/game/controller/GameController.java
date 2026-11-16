package br.edu.webchat.game.controller;

import br.edu.webchat.game.dto.CreateGameRequest;
import br.edu.webchat.game.dto.GameResponse;
import br.edu.webchat.game.service.GameService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Partidas entre colaboradores. Os comandos vao por REST, que devolve o erro direto a
 * quem jogou; o resultado de cada um chega aos dois jogadores por {@code /user/queue/games}.
 */
@RestController
@RequestMapping("/api/v1/games")
public class GameController {

	private final GameService gameService;

	public GameController(GameService gameService) {
		this.gameService = gameService;
	}

	@GetMapping
	public List<GameResponse> findMyGames(Authentication authentication) {
		return gameService.findMyGames(authentication.getName());
	}

	@PostMapping
	public ResponseEntity<GameResponse> create(@Valid @RequestBody CreateGameRequest request,
			Authentication authentication, UriComponentsBuilder uriBuilder) {
		GameResponse game = gameService.create(authentication.getName(), request);
		return ResponseEntity
				.created(uriBuilder.path("/api/v1/games/{id}").buildAndExpand(game.id()).toUri())
				.body(game);
	}

	@GetMapping("/{gameId}")
	public GameResponse findById(@PathVariable Long gameId, Authentication authentication) {
		return gameService.findById(gameId, authentication.getName());
	}

	@PostMapping("/{gameId}/accept")
	public GameResponse accept(@PathVariable Long gameId, Authentication authentication) {
		return gameService.accept(gameId, authentication.getName());
	}

	@PostMapping("/{gameId}/decline")
	public GameResponse decline(@PathVariable Long gameId, Authentication authentication) {
		return gameService.decline(gameId, authentication.getName());
	}

	@PostMapping("/{gameId}/cancel")
	public GameResponse cancel(@PathVariable Long gameId, Authentication authentication) {
		return gameService.cancel(gameId, authentication.getName());
	}

	@PostMapping("/{gameId}/resign")
	public GameResponse resign(@PathVariable Long gameId, Authentication authentication) {
		return gameService.resign(gameId, authentication.getName());
	}

	/** O corpo e a jogada no formato do jogo, por exemplo {@code {"cell": 4}} no jogo da velha. */
	@PostMapping("/{gameId}/moves")
	public GameResponse move(@PathVariable Long gameId, @RequestBody JsonNode move, Authentication authentication) {
		return gameService.move(gameId, authentication.getName(), move);
	}

}
