"use client";

import Avatar from "../Avatar";
import { gameInfo } from "@/lib/games";
import type { Game } from "@/lib/types";

interface Props {
  game: Game;
  onOpen: () => void;
  onDismiss: () => void;
}

/** Aviso discreto de desafio recebido. Não bloqueia a tela: a pessoa pode estar no meio de uma conversa. */
export default function GameInvite({ game, onOpen, onDismiss }: Props) {
  const info = gameInfo(game.type);

  return (
    <div className="fixed bottom-20 right-4 z-40 w-72 rounded-2xl border border-slate-700 bg-slate-900 p-4 shadow-2xl md:bottom-6">
      <div className="flex items-center gap-3">
        <Avatar name={game.challenger.name} url={game.challenger.avatarUrl ?? undefined} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{game.challenger.name}</p>
          <p className="text-xs text-slate-400">
            te desafiou para {info.icon} {info.name}
          </p>
        </div>
        <button type="button" onClick={onDismiss} title="Depois" className="self-start text-slate-500 hover:text-slate-300">
          ✕
        </button>
      </div>
      <button
        type="button"
        onClick={onOpen}
        className="mt-3 w-full rounded-lg bg-emerald-600 py-1.5 text-sm font-medium text-white transition hover:bg-emerald-500"
      >
        Ver desafio
      </button>
    </div>
  );
}
