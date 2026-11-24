"use client";

import { useState } from "react";
import type { ChessState } from "@/lib/types";

interface Props {
  state: ChessState;
  /** 0 joga de brancas (embaixo); 1 de pretas, e o tabuleiro é girado. */
  myIndex: 0 | 1;
  canPlay: boolean;
  onPlay: (uci: string) => void;
}

const COLUNAS = "abcdefgh";
const PROMOCOES = ["q", "r", "b", "n"];
const NOMES: Record<string, string> = { k: "rei", q: "dama", r: "torre", b: "bispo", n: "cavalo", p: "peão" };

/** Converte o campo de posições do FEN em 64 casas, da casa a8 (0) até h1 (63). */
function casasDoFen(fen: string): string[] {
  return fen
    .split(" ")[0]
    .split("/")
    .flatMap((fileira) => fileira.split("").flatMap((c) => (/\d/.test(c) ? Array(Number(c)).fill(".") : [c])));
}

function nomeDaCasa(indice: number): string {
  return `${COLUNAS[indice % 8]}${8 - Math.floor(indice / 8)}`;
}

/** Peça em FEN (`K` rei branco, `n` cavalo preto...) para o SVG em `public/pieces/chessnut`. */
function imagemDaPeca(peca: string): string {
  const cor = peca === peca.toUpperCase() ? "w" : "b";
  return `/pieces/chessnut/${cor}${peca.toUpperCase()}.svg`;
}

function Peca({ peca }: { peca: string }) {
  const cor = peca === peca.toUpperCase() ? "branco" : "preto";
  return (
    // Imagens locais e pequenas: o <Image> do Next não traria ganho no export estático.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={imagemDaPeca(peca)}
      alt={`${NOMES[peca.toLowerCase()]} ${cor}`}
      draggable={false}
      className="pointer-events-none absolute inset-[4%] z-10 h-[92%] w-[92%] select-none"
    />
  );
}

/** As regras ficam no servidor: o tabuleiro só oferece as jogadas de `state.moves`. */
export default function ChessBoard({ state, myIndex, canPlay, onPlay }: Props) {
  const [selecao, setSelecao] = useState<{ fen: string; casa: string | null; promocao: string | null }>({
    fen: state.fen,
    casa: null,
    promocao: null,
  });
  const atual = selecao.fen === state.fen ? selecao : { fen: state.fen, casa: null, promocao: null };

  const casas = casasDoFen(state.fen);
  const origens = new Set(canPlay ? state.moves.map((move) => move.slice(0, 2)) : []);
  const destinos = new Set(
    atual.casa ? state.moves.filter((move) => move.startsWith(atual.casa!)).map((move) => move.slice(2, 4)) : [],
  );
  const ultima = state.history[state.history.length - 1];
  const corDaVez = state.toMove === 0 ? "w" : "b";
  const reiEmXeque = state.check ? casas.findIndex((peca) => peca === (corDaVez === "w" ? "K" : "k")) : -1;

  function definir(casa: string | null, promocao: string | null = null) {
    setSelecao({ fen: state.fen, casa, promocao });
  }

  function clicar(indice: number) {
    if (!canPlay) return;
    const nome = nomeDaCasa(indice);

    if (atual.casa && destinos.has(nome)) {
      const jogadas = state.moves.filter((move) => move.startsWith(atual.casa! + nome));
      if (jogadas.length > 1) {
        definir(atual.casa, nome); // promoção: pergunta a peça
        return;
      }
      definir(null);
      onPlay(jogadas[0]);
      return;
    }

    definir(origens.has(nome) && nome !== atual.casa ? nome : null);
  }

  const indices = Array.from({ length: 64 }, (_, i) => i);
  const ordem = myIndex === 1 ? [...indices].reverse() : indices;

  const lances: [string, string | undefined][] = [];
  for (let i = 0; i < state.san.length; i += 2) lances.push([state.san[i], state.san[i + 1]]);

  return (
    <div className="flex w-full max-w-[min(100%,32rem)] flex-col gap-3">
      <div className="relative">
        <div className="grid aspect-square w-full grid-cols-8 grid-rows-8 overflow-hidden rounded-lg shadow-xl ring-1 ring-black/40">
          {ordem.map((indice) => {
            const nome = nomeDaCasa(indice);
            const clara = (Math.floor(indice / 8) + (indice % 8)) % 2 === 0;
            const peca = casas[indice];
            const ultimaJogada = ultima && (ultima.slice(0, 2) === nome || ultima.slice(2, 4) === nome);
            const selecionada = atual.casa === nome;
            const destino = destinos.has(nome);
            const naFileiraDeBaixo = myIndex === 0 ? indice >= 56 : indice <= 7;
            const naColunaDaEsquerda = myIndex === 0 ? indice % 8 === 0 : indice % 8 === 7;
            const corDoTexto = clara ? "text-[#779556]" : "text-[#ebecd0]";

            return (
              <button
                key={indice}
                type="button"
                onClick={() => clicar(indice)}
                aria-label={`${nome}${peca !== "." ? `, ${NOMES[peca.toLowerCase()]}` : ""}`}
                className={`relative min-h-0 min-w-0 ${clara ? "bg-[#ebecd0]" : "bg-[#779556]"} ${
                  canPlay && (origens.has(nome) || destino) ? "cursor-pointer" : "cursor-default"
                }`}
              >
                {ultimaJogada && <span className="absolute inset-0 bg-[#f5f682]/55" />}
                {selecionada && <span className="absolute inset-0 bg-[#f5f682]/80" />}
                {reiEmXeque === indice && (
                  <span className="absolute inset-0 bg-[radial-gradient(circle,rgba(255,0,0,0.9)_0%,rgba(231,0,0,0.6)_30%,rgba(169,0,0,0)_70%)]" />
                )}

                {peca !== "." && <Peca peca={peca} />}

                {destino &&
                  (peca === "." ? (
                    <span className="absolute left-1/2 top-1/2 z-20 h-[30%] w-[30%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-black/20" />
                  ) : (
                    <span className="absolute inset-0 z-20 rounded-full border-[0.35rem] border-black/20" />
                  ))}

                {naFileiraDeBaixo && (
                  <span className={`absolute bottom-0 right-1 z-0 text-[0.6rem] font-bold leading-none sm:text-xs ${corDoTexto}`}>
                    {nome[0]}
                  </span>
                )}
                {naColunaDaEsquerda && (
                  <span className={`absolute left-0.5 top-0.5 z-0 text-[0.6rem] font-bold leading-none sm:text-xs ${corDoTexto}`}>
                    {nome[1]}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {atual.promocao && (
          <div className="absolute inset-0 z-30 flex items-center justify-center rounded-lg bg-black/60">
            <div className="rounded-xl bg-slate-900 p-4 text-center shadow-xl">
              <p className="mb-3 text-sm text-slate-300">Promover para:</p>
              <div className="flex gap-2">
                {PROMOCOES.map((tipo) => {
                  const peca = corDaVez === "w" ? tipo.toUpperCase() : tipo;
                  return (
                    <button
                      key={tipo}
                      type="button"
                      onClick={() => {
                        const uci = `${atual.casa}${atual.promocao}${tipo}`;
                        definir(null);
                        onPlay(uci);
                      }}
                      title={NOMES[tipo]}
                      className="relative h-16 w-16 rounded-lg bg-[#ebecd0] transition hover:bg-[#f5f682]"
                    >
                      <Peca peca={peca} />
                    </button>
                  );
                })}
              </div>
              <button type="button" onClick={() => definir(null)} className="mt-3 text-xs text-slate-400 hover:text-slate-200">
                Cancelar
              </button>
            </div>
          </div>
        )}
      </div>

      {lances.length > 0 && (
        <ol className="flex max-h-24 flex-wrap gap-x-3 gap-y-1 overflow-y-auto rounded-lg bg-slate-900 p-2 font-mono text-xs text-slate-300">
          {lances.map(([brancas, pretas], i) => (
            <li key={i}>
              <span className="text-slate-500">{i + 1}.</span> {brancas} {pretas ?? ""}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
