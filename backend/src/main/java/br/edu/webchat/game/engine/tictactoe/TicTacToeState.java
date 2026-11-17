package br.edu.webchat.game.engine.tictactoe;

/**
 * @param board as 9 casas, linha por linha: {@code X}, {@code O} ou {@code .} para vazia
 * @param toMove indice de quem joga agora; quem desafiou (0) e o {@code X} e comeca
 */
public record TicTacToeState(String board, int toMove) {
}
