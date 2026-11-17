package br.edu.webchat.game.entity;

import java.util.List;

/**
 * Jogos para jogar sozinho. Rodam no navegador; o servidor so guarda o tempo das vitorias.
 * Cada jogo tem os proprios modos, e o ranking e separado por modo.
 */
public enum SoloGame {

	/** Iniciante 9x9 com 10 minas, intermediario 16x16 com 40, especialista 16x30 com 99. */
	MINESWEEPER(List.of("EASY", "MEDIUM", "HARD")),

	/** Paciencia (Klondike), virando uma ou tres cartas do monte por vez. */
	SOLITAIRE(List.of("DRAW_ONE", "DRAW_THREE"));

	private final List<String> modes;

	SoloGame(List<String> modes) {
		this.modes = modes;
	}

	public List<String> modes() {
		return modes;
	}

}
