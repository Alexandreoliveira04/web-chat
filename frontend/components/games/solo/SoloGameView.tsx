"use client";

import { useEffect, useState } from "react";
import Avatar from "../../Avatar";
import Minesweeper from "./Minesweeper";
import Solitaire from "./Solitaire";
import { soloApi } from "@/lib/api";
import { formatDuration, type SoloGameInfo } from "@/lib/games";
import type { MinesweeperLevel } from "@/lib/minesweeper";
import type { SoloRanking, SoloResult, User } from "@/lib/types";

interface Props {
  game: SoloGameInfo;
  me: User;
  onError: (message: string) => void;
}

/** Jogo solo com o seletor de modo e o ranking daquele modo. O jogo em si roda no navegador. */
export default function SoloGameView({ game, me, onError }: Props) {
  const [modo, setModo] = useState(game.modes[0].key);
  const [rodada, setRodada] = useState(0);
  const [ranking, setRanking] = useState<SoloRanking | null>(null);
  const [resultado, setResultado] = useState<SoloResult | null>(null);

  useEffect(() => {
    let ativo = true;
    soloApi
      .ranking(game.key, modo)
      .then((dados) => ativo && setRanking(dados))
      .catch(() => ativo && setRanking(null));
    return () => {
      ativo = false;
    };
  }, [game.key, modo]);

  function trocarModo(novo: string) {
    setModo(novo);
    setResultado(null);
    setRanking(null);
  }

  async function venceu(durationMs: number, moves?: number) {
    try {
      const resposta = await soloApi.submit(game.key, modo, durationMs, moves);
      setResultado(resposta);
      setRanking(resposta.ranking);
    } catch (falha) {
      onError(falha instanceof Error ? falha.message : "Não foi possível salvar o resultado");
    }
  }

  function jogarDeNovo() {
    setResultado(null);
    setRodada((atual) => atual + 1);
  }

  const chave = `${modo}-${rodada}`;

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 p-4 md:p-8 lg:flex-row lg:items-start">
      <div className="flex min-w-0 flex-1 flex-col items-center gap-4">
        <div className="flex flex-wrap justify-center gap-2">
          {game.modes.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => trocarModo(item.key)}
              className={`rounded-full px-3 py-1 text-sm transition ${
                modo === item.key ? "bg-emerald-600 text-white" : "border border-slate-700 text-slate-300 hover:border-emerald-500"
              }`}
            >
              {item.name}
            </button>
          ))}
        </div>

        {resultado && (
          <div className="w-full max-w-md rounded-xl border border-emerald-500/50 bg-emerald-500/10 p-4 text-center">
            <p className="text-lg font-semibold text-emerald-300">
              {resultado.personalBest ? "🏆 Novo recorde pessoal!" : "🎉 Você venceu!"}
            </p>
            <p className="text-sm text-slate-300">
              Tempo: {formatDuration(resultado.durationMs)}
              {resultado.ranking.me && ` · ${resultado.ranking.me.position}º no ranking`}
            </p>
            <button type="button" onClick={jogarDeNovo} className="mt-3 rounded-lg bg-emerald-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-emerald-500">
              Jogar de novo
            </button>
          </div>
        )}

        {game.key === "MINESWEEPER" && <Minesweeper key={chave} level={modo as MinesweeperLevel} onWin={(ms) => venceu(ms)} />}
        {game.key === "SOLITAIRE" && (
          <Solitaire key={chave} drawCount={modo === "DRAW_THREE" ? 3 : 1} onWin={(ms, moves) => venceu(ms, moves)} />
        )}
      </div>

      <aside className="w-full shrink-0 rounded-xl border border-slate-800 bg-slate-900 p-4 lg:w-72">
        <h3 className="mb-3 text-xs uppercase tracking-wide text-slate-500">
          Ranking · {game.modes.find((item) => item.key === modo)?.name}
        </h3>
        {!ranking && <p className="text-sm text-slate-500">Carregando…</p>}
        {ranking && ranking.top.length === 0 && <p className="text-sm text-slate-500">Ninguém venceu ainda. Seja o primeiro!</p>}
        <ol className="space-y-1">
          {ranking?.top.map((entrada) => (
            <li
              key={entrada.player.id}
              className={`flex items-center gap-2 rounded-lg p-1.5 text-sm ${entrada.player.id === me.id ? "bg-emerald-500/10" : ""}`}
            >
              <span className="w-6 text-center font-mono text-slate-400">
                {entrada.position === 1 ? "🥇" : entrada.position === 2 ? "🥈" : entrada.position === 3 ? "🥉" : entrada.position}
              </span>
              <Avatar name={entrada.player.name} url={entrada.player.avatarUrl ?? undefined} size="sm" />
              <span className="min-w-0 flex-1 truncate">{entrada.player.name}</span>
              <span className="font-mono text-slate-300" title={`${entrada.wins} vitória(s)`}>
                {formatDuration(entrada.bestDurationMs)}
              </span>
            </li>
          ))}
        </ol>
        {ranking?.me && ranking.me.position > ranking.top.length && (
          <p className="mt-3 border-t border-slate-800 pt-3 text-sm text-slate-400">
            Você: {ranking.me.position}º · {formatDuration(ranking.me.bestDurationMs)}
          </p>
        )}
        {ranking?.me && (
          <p className="mt-2 text-xs text-slate-500">
            {ranking.me.wins} vitória{ranking.me.wins === 1 ? "" : "s"} neste modo
          </p>
        )}
      </aside>
    </div>
  );
}
