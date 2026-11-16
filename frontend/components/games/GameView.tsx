"use client";

import { useState } from "react";
import Avatar from "../Avatar";
import TicTacToeBoard from "./TicTacToeBoard";
import { gameInfo, isInviteForMe, isMyTurn, statusLabel } from "@/lib/games";
import type { Game, GamePlayer, TicTacToeState, User } from "@/lib/types";

interface Props {
  game: Game;
  me: User;
  onMove: (move: unknown) => Promise<void>;
  onAccept: () => Promise<void>;
  onDecline: () => Promise<void>;
  onCancel: () => Promise<void>;
  onResign: () => Promise<void>;
  onRematch: () => Promise<void>;
  onOpenChat?: () => void;
}

/** Símbolo de cada jogador no tabuleiro: quem desafiou é o X (e começa). */
const SIMBOLOS = ["X", "O"] as const;

export default function GameView({ game, me, onMove, onAccept, onDecline, onCancel, onResign, onRematch, onOpenChat }: Props) {
  const [enviando, setEnviando] = useState(false);
  const [confirmarDesistencia, setConfirmarDesistencia] = useState(false);

  const info = gameInfo(game.type);
  const minhaVez = isMyTurn(game, me.id);
  const meuIndice = game.challenger.id === me.id ? 0 : 1;
  const terminada = game.status === "FINISHED";
  const status = statusLabel(game, me.id);

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
    <div className="mx-auto flex w-full max-w-xl flex-col items-center gap-6 p-4 md:p-8">
      <div className="text-center">
        <p className="text-sm uppercase tracking-wide text-slate-500">
          {info.icon} {info.name}
        </p>
        <p className={`mt-1 text-2xl font-semibold ${corDoStatus}`}>{status}</p>
      </div>

      <div className="flex w-full items-center justify-between gap-4">
        <Jogador jogador={game.challenger} simbolo={SIMBOLOS[0]} ehAVez={game.turnPlayerId === game.challenger.id} venceu={game.winnerId === game.challenger.id} />
        <span className="text-sm text-slate-500">vs</span>
        <Jogador jogador={game.opponent} simbolo={SIMBOLOS[1]} ehAVez={game.turnPlayerId === game.opponent.id} venceu={game.winnerId === game.opponent.id} alinharDireita />
      </div>

      {game.type === "TIC_TAC_TOE" && (
        <TicTacToeBoard
          state={game.state as TicTacToeState}
          canPlay={minhaVez && !enviando}
          mySymbol={SIMBOLOS[meuIndice]}
          onPlay={(cell) => executar(() => onMove({ cell }))}
        />
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

        {terminada && (game.result === "WIN" || game.result === "DRAW" || game.result === "RESIGNATION") && (
          <Botao onClick={() => executar(onRematch)} disabled={enviando} variante="principal">Revanche</Botao>
        )}

        {onOpenChat && <Botao onClick={onOpenChat}>Abrir conversa</Botao>}
      </div>
    </div>
  );
}

function Jogador({
  jogador,
  simbolo,
  ehAVez,
  venceu,
  alinharDireita = false,
}: {
  jogador: GamePlayer;
  simbolo: string;
  ehAVez: boolean;
  venceu: boolean;
  alinharDireita?: boolean;
}) {
  return (
    <div
      className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl border p-3 transition ${
        ehAVez ? "border-emerald-500 bg-emerald-500/10" : "border-slate-800 bg-slate-900"
      } ${alinharDireita ? "flex-row-reverse text-right" : ""}`}
    >
      <Avatar name={jogador.name} url={jogador.avatarUrl ?? undefined} size="sm" />
      <div className="min-w-0">
        <p className="truncate text-sm font-medium">
          {venceu && "🏆 "}
          {jogador.name}
        </p>
        <p className={`text-lg font-bold leading-none ${simbolo === "X" ? "text-sky-400" : "text-rose-400"}`}>{simbolo}</p>
      </div>
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
