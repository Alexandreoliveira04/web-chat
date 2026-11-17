"use client";

import { useEffect, useState } from "react";
import {
  autoComplete,
  canAutoComplete,
  deal,
  draw,
  isRed,
  isWon,
  move,
  rankLabel,
  sendToFoundation,
  type Card,
  type Pile,
  type SolitaireState,
} from "@/lib/solitaire";
import { clockNow, formatDuration } from "@/lib/games";

interface Props {
  drawCount: 1 | 3;
  onWin: (durationMs: number, moves: number) => void;
}

function mesmaPilha(a: Pile | null, b: Pile): boolean {
  if (!a || a.kind !== b.kind) return false;
  if (a.kind === "waste") return true;
  return a.index === (b as { index: number }).index;
}

/**
 * Paciência por toques: toca na carta (ou na sequência a partir dela) e depois na pilha de
 * destino. Toque duplo manda a carta para a fundação.
 */
export default function Solitaire({ drawCount, onWin }: Props) {
  const [historico, setHistorico] = useState<SolitaireState[]>(() => [deal(drawCount)]);
  const [selecao, setSelecao] = useState<Pile | null>(null);
  const [inicio, setInicio] = useState<number | null>(null);
  const [fim, setFim] = useState<number | null>(null);
  const [agora, setAgora] = useState(clockNow);

  const estado = historico[historico.length - 1];
  const venceu = isWon(estado);

  useEffect(() => {
    if (inicio === null || fim !== null) return;
    const timer = setInterval(() => setAgora(clockNow()), 500);
    return () => clearInterval(timer);
  }, [inicio, fim]);

  function aplicar(proximo: SolitaireState | null) {
    setSelecao(null);
    if (!proximo || venceu) return;

    const comeco = inicio ?? clockNow();
    if (inicio === null) setInicio(comeco);
    setHistorico((atual) => [...atual, proximo]);

    if (isWon(proximo)) {
      const termino = clockNow();
      setFim(termino);
      onWin(termino - comeco, proximo.moves);
    }
  }

  function reiniciar() {
    setHistorico([deal(drawCount)]);
    setSelecao(null);
    setInicio(null);
    setFim(null);
  }

  function desfazer() {
    if (historico.length > 1 && !venceu) {
      setHistorico((atual) => atual.slice(0, -1));
      setSelecao(null);
    }
  }

  /** Toque numa pilha: com algo selecionado tenta mover para ela; senão seleciona. */
  function tocar(pilha: Pile, selecionavel: boolean) {
    if (selecao && !mesmaPilha(selecao, pilha)) {
      const resultado = move(estado, selecao, pilha);
      if (resultado) {
        aplicar(resultado);
        return;
      }
    }
    setSelecao(selecionavel && !(selecao && mesmaPilha(selecao, pilha) && selecao.kind !== "tableau") ? pilha : null);
  }

  const decorrido = inicio === null ? 0 : (fim ?? agora) - inicio;
  const visiveisNoDescarte = estado.waste.slice(-estado.drawCount);
  const selecionada = (pilha: Pile, cardIndex?: number) => {
    if (!selecao || !mesmaPilha(selecao, pilha)) return false;
    if (selecao.kind !== "tableau" || cardIndex === undefined) return true;
    return cardIndex >= (selecao.cardIndex ?? Infinity);
  };

  return (
    <div className="flex w-full max-w-2xl flex-col gap-3">
      <div className="flex items-center justify-between rounded-xl bg-slate-900 px-4 py-2 text-sm">
        <span className="font-mono text-slate-200">⏱ {formatDuration(decorrido)}</span>
        <span className="text-slate-400">{estado.moves} jogadas</span>
        <div className="flex gap-2">
          <button type="button" onClick={desfazer} disabled={historico.length <= 1 || venceu} className="rounded-md border border-slate-700 px-2 py-1 text-slate-300 hover:border-emerald-500 disabled:opacity-40">
            ↶ Desfazer
          </button>
          <button type="button" onClick={reiniciar} className="rounded-md border border-slate-700 px-2 py-1 text-slate-300 hover:border-emerald-500">
            Novo jogo
          </button>
        </div>
      </div>

      <div className="grid grid-cols-7 gap-1.5 sm:gap-2">
        <button type="button" onClick={() => aplicar(draw(estado))} title="Monte" className="aspect-[5/7]">
          {estado.stock.length > 0 ? (
            <Verso />
          ) : (
            <div className="flex h-full items-center justify-center rounded-md border-2 border-dashed border-slate-700 text-xl text-slate-600">↻</div>
          )}
        </button>

        <div className="relative col-span-2 aspect-[10/7]">
          {visiveisNoDescarte.map((carta, i) => {
            const topo = i === visiveisNoDescarte.length - 1;
            return (
              <div
                key={carta.id}
                className="absolute top-0 h-full"
                style={{ left: `${i * 22}%`, width: "50%" }}
                onClick={topo ? () => tocar({ kind: "waste" }, true) : undefined}
                onDoubleClick={topo ? () => aplicar(sendToFoundation(estado, { kind: "waste" })) : undefined}
              >
                <Frente carta={carta} selecionada={topo && selecionada({ kind: "waste" })} />
              </div>
            );
          })}
        </div>

        {estado.foundations.map((pilha, index) => {
          const topo = pilha[pilha.length - 1];
          return (
            <div key={index} className="aspect-[5/7]" onClick={() => tocar({ kind: "foundation", index }, Boolean(topo))}>
              {topo ? (
                <Frente carta={topo} selecionada={selecionada({ kind: "foundation", index })} />
              ) : (
                <div className="flex h-full items-center justify-center rounded-md border-2 border-slate-700 text-lg text-slate-700">A</div>
              )}
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-7 gap-1.5 sm:gap-2">
        {estado.tableau.map((coluna, index) => (
          <div key={index} className="flex min-h-40 flex-col" onClick={() => coluna.length === 0 && tocar({ kind: "tableau", index }, false)}>
            {coluna.length === 0 && <div className="aspect-[5/7] rounded-md border-2 border-dashed border-slate-700" />}
            {coluna.map((carta, cardIndex) => (
              <div
                key={carta.id}
                className={cardIndex === 0 ? "" : coluna[cardIndex - 1].faceUp ? "-mt-[100%]" : "-mt-[118%]"}
                onClick={(evento) => {
                  evento.stopPropagation();
                  tocar({ kind: "tableau", index, cardIndex }, carta.faceUp);
                }}
                onDoubleClick={() => {
                  if (cardIndex === coluna.length - 1) aplicar(sendToFoundation(estado, { kind: "tableau", index }));
                }}
              >
                {carta.faceUp ? <Frente carta={carta} selecionada={selecionada({ kind: "tableau", index }, cardIndex)} /> : <Verso />}
              </div>
            ))}
          </div>
        ))}
      </div>

      {canAutoComplete(estado) && (
        <button type="button" onClick={() => aplicar(autoComplete(estado))} className="self-center rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500">
          Completar automaticamente
        </button>
      )}

      <p className="text-center text-xs text-slate-500">
        Toque na carta e depois no destino. Toque duplo manda para a fundação. Toque no monte para virar {drawCount === 1 ? "uma carta" : "três cartas"}.
      </p>
    </div>
  );
}

function Frente({ carta, selecionada }: { carta: Card; selecionada: boolean }) {
  const cor = isRed(carta) ? "text-red-600" : "text-slate-900";
  return (
    <div
      className={`relative flex aspect-[5/7] w-full cursor-pointer select-none flex-col rounded-md border bg-white p-0.5 shadow ${
        selecionada ? "border-emerald-400 ring-2 ring-emerald-400" : "border-slate-300"
      } ${cor}`}
    >
      <span className="text-[clamp(9px,2.6vw,15px)] font-bold leading-none">
        {rankLabel(carta)}
        <span className="ml-px">{carta.suit}</span>
      </span>
      <span className="absolute inset-0 top-1/4 flex items-center justify-center text-[clamp(14px,5vw,32px)]">{carta.suit}</span>
    </div>
  );
}

function Verso() {
  return (
    <div className="aspect-[5/7] w-full rounded-md border border-slate-300 bg-[repeating-linear-gradient(45deg,#1e40af_0_4px,#1d4ed8_4px_8px)] shadow" />
  );
}
