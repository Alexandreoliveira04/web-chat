package br.edu.webchat.game.engine.chess;

import java.util.List;

/**
 * Partida de xadrez. A fonte da verdade e {@code history}: o tabuleiro e refeito jogando-o
 * desde a posicao inicial, o que preserva o que o FEN sozinho perde (repeticao de posicao).
 * Os demais campos sao derivados e vao prontos para o frontend.
 *
 * @param fen posicao atual em FEN
 * @param toMove indice de quem joga agora; quem desafiou (0) joga de brancas
 * @param history jogadas feitas, em UCI ({@code e2e4}, {@code e7e8q})
 * @param san as mesmas jogadas em notacao algebrica ({@code e4}, {@code Qxf7#}), para exibir
 * @param moves jogadas legais de quem joga agora, em UCI
 * @param check o rei de quem joga agora esta em xeque
 * @param ending como a partida terminou pelas regras, ou nulo se continua
 */
public record ChessState(
		String fen,
		int toMove,
		List<String> history,
		List<String> san,
		List<String> moves,
		boolean check,
		ChessEnding ending
) {
}
