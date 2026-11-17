package br.edu.webchat.game.engine;

import br.edu.webchat.game.entity.GameType;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Regras de um jogo de duas pessoas. Cada jogo e uma classe que implementa esta interface
 * e e registrada como bean; o {@code GameService} cuida de quem joga, de persistir e de
 * avisar os jogadores, e o motor so conhece o tabuleiro.
 *
 * <p>Os jogadores sao identificados pelo indice: 0 para quem desafiou e 1 para o
 * adversario. O estado {@code S} e serializado em JSON e enviado como esta ao frontend,
 * entao nao deve guardar nada que os jogadores nao possam ver.
 *
 * @param <S> estado do tabuleiro, imutavel e serializavel pelo Jackson
 */
public interface GameEngine<S> {

	GameType type();

	Class<S> stateType();

	S initialState();

	/** Indice (0 ou 1) de quem deve jogar agora. */
	int playerToMove(S state);

	/**
	 * Aplica a jogada e devolve o novo estado, sem alterar o recebido.
	 *
	 * @param move JSON enviado pelo navegador; o formato e definido por cada jogo
	 * @throws br.edu.webchat.shared.exception.BadRequestException se a jogada for invalida
	 */
	S applyMove(S state, int player, JsonNode move);

	GameOutcome outcome(S state);

}
