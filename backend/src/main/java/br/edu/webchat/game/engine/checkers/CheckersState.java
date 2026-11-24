package br.edu.webchat.game.engine.checkers;

import java.util.List;

/**
 * Tabuleiro de damas 8x8. As casas sao numeradas de 0 a 63, linha por linha, a partir do
 * canto superior esquerdo; so as casas escuras ({@code (linha + coluna)} impar) sao usadas.
 *
 * @param board 64 caracteres: {@code w}/{@code W} pedra/dama branca, {@code b}/{@code B} pedra/dama
 *     preta e {@code .} vazia. As brancas sao de quem desafiou (jogador 0), comecam embaixo e saem jogando
 * @param toMove indice de quem joga agora
 * @param quietMoves jogadas seguidas so com damas e sem captura; ao chegar ao limite, empate
 * @param moves jogadas legais de quem joga agora, cada uma como o caminho de casas percorridas;
 *     enviadas para o frontend nao precisar repetir as regras
 * @param lastMove caminho da ultima jogada, para destacar no tabuleiro
 * @param lastCaptured casas das pecas capturadas na ultima jogada
 */
public record CheckersState(
		String board,
		int toMove,
		int quietMoves,
		List<List<Integer>> moves,
		List<Integer> lastMove,
		List<Integer> lastCaptured
) {
}
