"use client";

import { useEffect, useState } from "react";
import { chord, flagsLeft, newBoard, reveal, toggleFlag, type MinesweeperBoard, type MinesweeperLevel } from "@/lib/minesweeper";
import { clockNow, formatDuration } from "@/lib/games";

interface Props {
  level: MinesweeperLevel;
  onWin: (durationMs: number) => void;
}

const CORES_DOS_NUMEROS = ["", "text-blue-400", "text-green-400", "text-red-400", "text-indigo-300", "text-amber-500", "text-teal-300", "text-slate-100", "text-slate-400"];

export default function Minesweeper({ level, onWin }: Props) {
  const [board, setBoard] = useState<MinesweeperBoard>(() => newBoard(level));
  const [inicio, setInicio] = useState<number | null>(null);
  const [fim, setFim] = useState<number | null>(null);
  const [agora, setAgora] = useState(clockNow);
  const [marcando, setMarcando] = useState(false);

  useEffect(() => {
    if (board.status !== "playing") return;
    const timer = setInterval(() => setAgora(clockNow()), 500);
    return () => clearInterval(timer);
  }, [board.status]);

  function aplicar(proximo: MinesweeperBoard) {
    const comeco = inicio ?? clockNow();
    if (inicio === null && proximo.status !== "ready") setInicio(comeco);

    if (proximo.status === "won" || proximo.status === "lost") {
      const termino = clockNow();
      setFim(termino);
      if (proximo.status === "won") onWin(termino - comeco);
    }
    setBoard(proximo);
  }

  function clicar(indice: number) {
    const celula = board.cells[indice];
    if (marcando) aplicar(toggleFlag(board, indice));
    else if (celula.revealed) aplicar(chord(board, indice));
    else aplicar(reveal(board, indice));
  }

  function reiniciar() {
    setBoard(newBoard(level));
    setInicio(null);
    setFim(null);
    setMarcando(false);
  }

  const decorrido = inicio === null ? 0 : (fim ?? agora) - inicio;
  const rosto = board.status === "won" ? "😎" : board.status === "lost" ? "😵" : "🙂";
  const terminou = board.status === "won" || board.status === "lost";

  return (
    <div className="flex w-full flex-col items-center gap-3">
      <div className="flex w-full max-w-md items-center justify-between rounded-xl bg-slate-900 px-4 py-2 font-mono">
        <span title="Minas restantes" className="text-lg text-red-400">💣 {flagsLeft(board)}</span>
        <button type="button" onClick={reiniciar} title="Novo jogo" className="text-2xl transition hover:scale-110">
          {rosto}
        </button>
        <span title="Tempo" className="text-lg text-slate-200">⏱ {formatDuration(decorrido)}</span>
      </div>

      <button
        type="button"
        onClick={() => setMarcando((atual) => !atual)}
        disabled={terminou}
        className={`rounded-lg border px-3 py-1 text-sm transition disabled:opacity-40 ${
          marcando ? "border-red-500 bg-red-500/15 text-red-300" : "border-slate-700 text-slate-300"
        }`}
      >
        {marcando ? "🚩 Marcando bandeiras" : "⛏ Abrindo casas"} <span className="text-xs text-slate-500">(toque para trocar)</span>
      </button>

      <div className="max-w-full overflow-x-auto rounded-lg border-2 border-slate-700 bg-slate-800 p-1">
        <div className="grid gap-px" style={{ gridTemplateColumns: `repeat(${board.cols}, 1.75rem)` }}>
          {board.cells.map((celula, indice) => (
            <button
              key={indice}
              type="button"
              onClick={() => clicar(indice)}
              onContextMenu={(evento) => {
                evento.preventDefault();
                aplicar(toggleFlag(board, indice));
              }}
              disabled={terminou}
              className={`flex h-7 w-7 items-center justify-center text-sm font-bold ${
                celula.revealed
                  ? board.exploded === indice
                    ? "bg-red-600"
                    : "bg-slate-950"
                  : "bg-slate-600 hover:bg-slate-500 disabled:hover:bg-slate-600"
              }`}
            >
              {celula.revealed
                ? celula.mine
                  ? "💣"
                  : celula.adjacent > 0 && <span className={CORES_DOS_NUMEROS[celula.adjacent]}>{celula.adjacent}</span>
                : celula.flagged && "🚩"}
            </button>
          ))}
        </div>
      </div>

      <p className="text-center text-xs text-slate-500">
        Clique para abrir; botão direito (ou o modo bandeira) para marcar. Clicar num número com as bandeiras certas abre as vizinhas.
      </p>

      {board.status === "lost" && (
        <button type="button" onClick={reiniciar} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500">
          Tentar de novo
        </button>
      )}
    </div>
  );
}
