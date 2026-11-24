"use client";

import { useState } from "react";
import type { CheckersState } from "@/lib/types";

interface Props {
  state: CheckersState;
  /** 0 joga de brancas (embaixo); 1 de pretas, e o tabuleiro é girado. */
  myIndex: 0 | 1;
  canPlay: boolean;
  onPlay: (path: number[]) => void;
}

const MINHAS: Record<0 | 1, string> = { 0: "wW", 1: "bB" };

function comecaCom(caminho: number[], prefixo: number[]) {
  return prefixo.every((casa, i) => caminho[i] === casa);
}

/**
 * As regras ficam no servidor: o tabuleiro só oferece as jogadas de `state.moves`. Numa
 * captura múltipla, a pessoa clica a peça e depois cada casa de parada, uma de cada vez.
 */
export default function CheckersBoard({ state, myIndex, canPlay, onPlay }: Props) {
  // O caminho em montagem pertence a um tabuleiro; se o tabuleiro muda, ele é descartado.
  const [selecao, setSelecao] = useState<{ board: string; caminho: number[] }>({ board: state.board, caminho: [] });
  const caminho = selecao.board === state.board ? selecao.caminho : [];

  const candidatas = canPlay ? state.moves.filter((move) => comecaCom(move, caminho)) : [];
  const origens = new Set(canPlay ? state.moves.map((move) => move[0]) : []);
  const proximas = new Set(caminho.length ? candidatas.map((move) => move[caminho.length]).filter((casa) => casa !== undefined) : []);
  const ultima = new Set(state.lastMove);
  const capturadas = new Set(state.lastCaptured);

  function escolher(novo: number[]) {
    setSelecao({ board: state.board, caminho: novo });
  }

  function clicar(casa: number) {
    if (!canPlay) return;

    if (caminho.length > 0 && proximas.has(casa)) {
      const novo = [...caminho, casa];
      const completa = candidatas.some((move) => move.length === novo.length && comecaCom(move, novo));
      if (completa) {
        escolher([]);
        onPlay(novo);
      } else {
        escolher(novo);
      }
      return;
    }

    escolher(origens.has(casa) ? [casa] : []);
  }

  const casas = Array.from({ length: 64 }, (_, i) => i);
  const ordem = myIndex === 1 ? [...casas].reverse() : casas;

  return (
    <div className="w-full max-w-[min(100%,32rem)]">
      <div className="grid aspect-square w-full grid-cols-8 grid-rows-8 overflow-hidden rounded-lg border-[6px] border-[#5c3a1e] shadow-xl">
        {ordem.map((casa) => {
          const linha = Math.floor(casa / 8);
          const coluna = casa % 8;
          const escura = (linha + coluna) % 2 === 1;
          const peca = state.board[casa];
          const minha = MINHAS[myIndex].includes(peca);
          const selecionada = caminho.includes(casa);
          const destino = proximas.has(casa);
          const jogavel = caminho.length === 0 && origens.has(casa);

          return (
            <button
              key={casa}
              type="button"
              disabled={!escura}
              onClick={() => clicar(casa)}
              aria-label={`Casa ${casa}${peca !== "." ? `, ${peca}` : ""}`}
              className={`relative min-h-0 min-w-0 ${escura ? "bg-[#b58863]" : "bg-[#f0d9b5]"} ${
                canPlay && (origens.has(casa) || destino) ? "cursor-pointer" : "cursor-default"
              }`}
            >
              {ultima.has(casa) && <span className="absolute inset-0 bg-[#f5f682]/45" />}
              {selecionada && <span className="absolute inset-0 bg-[#f5f682]/80" />}

              {peca !== "." && (
                <span
                  className={`absolute inset-[12%] z-10 flex items-center justify-center rounded-full shadow-[0_3px_0_rgba(0,0,0,0.35),0_4px_6px_rgba(0,0,0,0.4)] ${
                    peca.toLowerCase() === "w"
                      ? "bg-[radial-gradient(circle_at_35%_30%,#ffffff,#e7e2d8_60%,#c9c1b2)] text-[#a16207]"
                      : "bg-[radial-gradient(circle_at_35%_30%,#5b5b5b,#262626_60%,#0a0a0a)] text-[#facc15]"
                  } ${jogavel && minha ? "ring-[3px] ring-emerald-400" : ""}`}
                >
                  <span className="absolute inset-[18%] rounded-full border border-black/15" />
                  {(peca === "W" || peca === "B") && <CoroaIcon />}
                </span>
              )}
              {peca === "." && capturadas.has(casa) && (
                <span className="absolute left-1/2 top-1/2 h-[18%] w-[18%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-red-500/50" />
              )}
              {destino && (
                <span className="absolute left-1/2 top-1/2 z-20 h-[30%] w-[30%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-black/25" />
              )}
            </button>
          );
        })}
      </div>
      {canPlay && caminho.length > 1 && (
        <p className="mt-2 text-center text-xs text-slate-400">Continue a captura: escolha a próxima casa.</p>
      )}
    </div>
  );
}

function CoroaIcon() {
  return (
    <svg viewBox="0 0 24 24" className="relative h-[55%] w-[55%]" fill="currentColor" aria-hidden="true">
      <path d="M3 18h18v2H3zM3 7l4.5 4L12 4l4.5 7L21 7l-2 9H5z" />
    </svg>
  );
}
