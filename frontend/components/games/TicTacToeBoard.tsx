"use client";

import type { TicTacToeState } from "@/lib/types";

const LINHAS = [
  [0, 1, 2], [3, 4, 5], [6, 7, 8],
  [0, 3, 6], [1, 4, 7], [2, 5, 8],
  [0, 4, 8], [2, 4, 6],
];

/** Só para destacar; quem decide o vencedor é o servidor. */
function linhaVencedora(board: string): number[] | null {
  return LINHAS.find(([a, b, c]) => board[a] !== "." && board[a] === board[b] && board[a] === board[c]) ?? null;
}

interface Props {
  state: TicTacToeState;
  /** Pode clicar: é a minha vez e não há jogada sendo enviada. */
  canPlay: boolean;
  mySymbol: "X" | "O" | null;
  onPlay: (cell: number) => void;
}

export default function TicTacToeBoard({ state, canPlay, mySymbol, onPlay }: Props) {
  const vencedora = linhaVencedora(state.board);

  return (
    <div className="grid aspect-square w-full max-w-xs grid-cols-3 gap-2 rounded-2xl bg-slate-800 p-2 sm:max-w-sm">
      {state.board.split("").map((casa, indice) => {
        const vazia = casa === ".";
        const destaque = vencedora?.includes(indice);
        const clicavel = canPlay && vazia;

        return (
          <button
            key={indice}
            type="button"
            disabled={!clicavel}
            onClick={() => onPlay(indice)}
            aria-label={vazia ? `Casa ${indice + 1}, vazia` : `Casa ${indice + 1}, ${casa}`}
            className={`group flex items-center justify-center rounded-xl text-5xl font-bold transition sm:text-6xl ${
              destaque ? "bg-emerald-500/25" : "bg-slate-900"
            } ${clicavel ? "cursor-pointer hover:bg-slate-700" : "cursor-default"}`}
          >
            {vazia ? (
              clicavel && mySymbol && <span className="text-slate-600 opacity-0 transition group-hover:opacity-100">{mySymbol}</span>
            ) : (
              <span className={casa === "X" ? "text-sky-400" : "text-rose-400"}>{casa}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
