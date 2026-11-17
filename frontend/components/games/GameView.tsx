"use client";

import { useEffect, useState } from "react";
import Avatar from "../Avatar";
import TicTacToeBoard from "./TicTacToeBoard";
import CheckersBoard from "./CheckersBoard";
import ChessBoard from "./ChessBoard";
import { clockNow, gameInfo, isInviteForMe, isMyTurn, opponentOf, statusLabel, timeLeft } from "@/lib/games";
import type { CheckersState, ChessEnding, ChessState, Game, GamePlayer, TicTacToeState, User } from "@/lib/types";

interface Props {
  game: Game;
  me: User;
  onMove: (move: unknown) => Promise<void>;
  onAccept: () => Promise<void>;
  onDecline: () => Promise<void>;
  onCancel: () => Promise<void>;
  onResign: () => Promise<void>;
  onOfferDraw: () => Promise<void>;
  onAcceptDraw: () => Promise<void>;
  onDeclineDraw: () => Promise<void>;
  onRematch: () => Promise<void>;
  onOpenChat?: () => void;
}

const FIM_DO_XADREZ: Record<ChessEnding, string> = {
  CHECKMATE: "Xeque-mate",
  STALEMATE: "Afogamento: quem joga não está em xeque e não tem lance",
  REPETITION: "A mesma posição se repetiu três vezes",
  INSUFFICIENT_MATERIAL: "Nenhum lado tem peças para dar mate",
  FIFTY_MOVES: "50 lances de cada lado sem captura nem movimento de peão",
};

/** Explica o estado do tabuleiro quando o status sozinho não basta (xeque, motivo do empate). */
function detalhe(game: Game): string | null {
  if (game.type === "CHESS") {
    const state = game.state as ChessState;
    if (game.status === "IN_PROGRESS" && state.check) return "Xeque!";
    if (game.status === "FINISHED" && state.ending) return FIM_DO_XADREZ[state.ending];
  }
  if (game.type === "CHECKERS" && game.result === "DRAW") {
    return "20 lances de cada lado só com damas, sem captura";
  }
  return null;
}

/** Relógio como `m:ss`; nos últimos 10 segundos, com décimos. */
function formatarRelogio(ms: number): string {
  if (ms < 10_000) return `0:0${(ms / 1000).toFixed(1)}`;
  const total = Math.ceil(ms / 1000);
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

/** Hora atual que se atualiza sozinha enquanto algum relógio está correndo. */
function useAgora(correndo: boolean): number {
  const [agora, setAgora] = useState(clockNow);
  useEffect(() => {
    if (!correndo) return;
    const timer = setInterval(() => setAgora(clockNow()), 100);
    return () => clearInterval(timer);
  }, [correndo]);
  return agora;
}

function corDoLado(lado: string) {
  if (lado === "X") return "text-sky-400";
  if (lado === "O") return "text-rose-400";
  return "text-slate-300";
}

export default function GameView({
  game,
  me,
  onMove,
  onAccept,
  onDecline,
  onCancel,
  onResign,
  onOfferDraw,
  onAcceptDraw,
  onDeclineDraw,
  onRematch,
  onOpenChat,
}: Props) {
  const [enviando, setEnviando] = useState(false);
  const [confirmarDesistencia, setConfirmarDesistencia] = useState(false);

  const info = gameInfo(game.type);
  const minhaVez = isMyTurn(game, me.id);
  const meuIndice: 0 | 1 = game.challenger.id === me.id ? 0 : 1;
  const lados = info.sides;
  const explicacao = detalhe(game);
  const terminada = game.status === "FINISHED";
  const status = statusLabel(game, me.id);
  const agora = useAgora(game.status === "IN_PROGRESS" && Boolean(game.clock?.runningPlayerId));
  const relogio = (jogadorId: number) => {
    const ms = timeLeft(game, jogadorId, agora);
    return ms === null ? null : { ms, correndo: game.clock?.runningPlayerId === jogadorId && game.status === "IN_PROGRESS" };
  };
  const outro = opponentOf(game, me.id);
  const aceitaEmpate = info.timed && game.status === "IN_PROGRESS";

  async function executar(acao: () => Promise<unknown>) {
    if (enviando) return;
    setEnviando(true);
    try {
      await acao();
    } catch {
      // o erro já aparece no toast da página
    } finally {
      setEnviando(false);
    }
  }

  const corDoStatus = terminada
    ? game.winnerId === me.id
      ? "text-emerald-400"
      : game.winnerId
        ? "text-rose-400"
        : "text-slate-300"
    : minhaVez
      ? "text-emerald-400"
      : "text-slate-300";

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col items-center gap-6 p-4 md:p-8">
      <div className="text-center">
        <p className="text-sm uppercase tracking-wide text-slate-500">
          {info.icon} {info.name}
        </p>
        <p className={`mt-1 text-2xl font-semibold ${corDoStatus}`}>{status}</p>
        {explicacao && <p className="mt-1 text-sm text-amber-300">{explicacao}</p>}
      </div>

      <div className="flex w-full items-center justify-between gap-4">
        <Jogador
          jogador={game.challenger}
          lado={lados[0]}
          ehAVez={game.turnPlayerId === game.challenger.id}
          venceu={game.winnerId === game.challenger.id}
          relogio={relogio(game.challenger.id)}
        />
        <span className="text-sm text-slate-500">vs</span>
        <Jogador
          jogador={game.opponent}
          lado={lados[1]}
          ehAVez={game.turnPlayerId === game.opponent.id}
          venceu={game.winnerId === game.opponent.id}
          relogio={relogio(game.opponent.id)}
          alinharDireita
        />
      </div>

      {game.type === "TIC_TAC_TOE" && (
        <TicTacToeBoard
          state={game.state as TicTacToeState}
          canPlay={minhaVez && !enviando}
          mySymbol={lados[meuIndice] as "X" | "O"}
          onPlay={(cell) => executar(() => onMove({ cell }))}
        />
      )}

      {game.type === "CHECKERS" && (
        <CheckersBoard
          state={game.state as CheckersState}
          myIndex={meuIndice}
          canPlay={minhaVez && !enviando}
          onPlay={(path) => executar(() => onMove({ path }))}
        />
      )}

      {game.type === "CHESS" && (
        <ChessBoard
          state={game.state as ChessState}
          myIndex={meuIndice}
          canPlay={minhaVez && !enviando}
          onPlay={(move) => executar(() => onMove({ move }))}
        />
      )}

      {aceitaEmpate && game.drawOfferedBy === outro.id && (
        <div className="flex w-full flex-wrap items-center justify-center gap-3 rounded-xl border border-amber-500/50 bg-amber-500/10 p-3">
          <span className="text-sm text-amber-200">🤝 {outro.name.split(" ")[0]} propõe empate</span>
          <Botao onClick={() => executar(onDeclineDraw)} disabled={enviando}>Recusar</Botao>
          <Botao onClick={() => executar(onAcceptDraw)} disabled={enviando} variante="principal">Aceitar empate</Botao>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-center gap-3">
        {isInviteForMe(game, me.id) && (
          <>
            <Botao onClick={() => executar(onDecline)} disabled={enviando} variante="perigo">Recusar</Botao>
            <Botao onClick={() => executar(onAccept)} disabled={enviando} variante="principal">Aceitar desafio</Botao>
          </>
        )}

        {game.status === "INVITED" && game.challenger.id === me.id && (
          <Botao onClick={() => executar(onCancel)} disabled={enviando}>Cancelar desafio</Botao>
        )}

        {game.status === "IN_PROGRESS" &&
          (confirmarDesistencia ? (
            <>
              <span className="text-sm text-slate-400">Desistir da partida?</span>
              <Botao onClick={() => setConfirmarDesistencia(false)}>Continuar jogando</Botao>
              <Botao onClick={() => executar(onResign)} disabled={enviando} variante="perigo">Desistir</Botao>
            </>
          ) : (
            <Botao onClick={() => setConfirmarDesistencia(true)}>Desistir</Botao>
          ))}

        {aceitaEmpate && !confirmarDesistencia && game.drawOfferedBy === null && (
          <Botao onClick={() => executar(onOfferDraw)} disabled={enviando}>🤝 Propor empate</Botao>
        )}
        {aceitaEmpate && game.drawOfferedBy === me.id && (
          <span className="text-sm text-slate-400">Empate proposto, aguardando resposta…</span>
        )}

        {terminada && game.result !== "DECLINED" && game.result !== "CANCELED" && (
          <Botao onClick={() => executar(onRematch)} disabled={enviando} variante="principal">Revanche</Botao>
        )}

        {onOpenChat && <Botao onClick={onOpenChat}>Abrir conversa</Botao>}
      </div>
    </div>
  );
}

function Jogador({
  jogador,
  lado,
  ehAVez,
  venceu,
  relogio,
  alinharDireita = false,
}: {
  jogador: GamePlayer;
  lado: string;
  ehAVez: boolean;
  venceu: boolean;
  relogio: { ms: number; correndo: boolean } | null;
  alinharDireita?: boolean;
}) {
  const acabando = relogio !== null && relogio.ms < 20_000;
  return (
    <div
      className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl border p-3 transition ${
        ehAVez ? "border-emerald-500 bg-emerald-500/10" : "border-slate-800 bg-slate-900"
      } ${alinharDireita ? "flex-row-reverse text-right" : ""}`}
    >
      <Avatar name={jogador.name} url={jogador.avatarUrl ?? undefined} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {venceu && "🏆 "}
          {jogador.name}
        </p>
        <p className={`font-bold leading-none ${lado.length === 1 ? "text-lg" : "text-xs uppercase tracking-wide"} ${corDoLado(lado)}`}>{lado}</p>
      </div>
      {relogio && (
        <span
          title="Tempo restante"
          className={`shrink-0 rounded-lg px-2 py-1 font-mono text-lg font-bold tabular-nums transition ${
            relogio.correndo
              ? acabando
                ? "animate-pulse bg-red-600 text-white"
                : "bg-slate-100 text-slate-900"
              : acabando
                ? "bg-red-900/60 text-red-200"
                : "bg-slate-800 text-slate-400"
          }`}
        >
          {formatarRelogio(relogio.ms)}
        </span>
      )}
    </div>
  );
}

export function Botao({
  children,
  onClick,
  disabled,
  variante = "neutro",
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  variante?: "neutro" | "principal" | "perigo";
}) {
  const estilo = {
    neutro: "border border-slate-700 text-slate-200 hover:border-emerald-500 hover:text-emerald-400",
    principal: "bg-emerald-600 text-white hover:bg-emerald-500",
    perigo: "bg-red-600/90 text-white hover:bg-red-500",
  }[variante];

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`rounded-lg px-4 py-2 text-sm font-medium transition disabled:opacity-50 ${estilo}`}
    >
      {children}
    </button>
  );
}
