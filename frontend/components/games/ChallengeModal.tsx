"use client";

import { useState } from "react";
import Modal from "../Modal";
import Avatar from "../Avatar";
import { VERSUS_GAMES } from "@/lib/games";
import type { GameType, Participant, User } from "@/lib/types";

interface Props {
  /** Quem pode ser desafiado: todos os colaboradores, ou só quem está na conversa. */
  candidates: (User | Participant)[];
  initialType?: string;
  onClose: () => void;
  onChallenge: (type: GameType, opponentId: number) => Promise<void>;
}

export default function ChallengeModal({ candidates, initialType, onClose, onChallenge }: Props) {
  const disponiveis = VERSUS_GAMES.filter((game) => game.available);
  const [tipo, setTipo] = useState(initialType ?? disponiveis[0]?.key);
  const [adversario, setAdversario] = useState<number | null>(candidates.length === 1 ? candidates[0].id : null);
  const [busca, setBusca] = useState("");
  const [enviando, setEnviando] = useState(false);

  const termo = busca.trim().toLowerCase();
  const filtrados = candidates.filter((pessoa) => pessoa.name.toLowerCase().includes(termo));

  async function desafiar() {
    if (!tipo || adversario === null) return;
    setEnviando(true);
    try {
      await onChallenge(tipo as GameType, adversario);
      onClose();
    } catch {
      // o erro aparece no toast da página
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal title="Desafiar para um jogo" onClose={onClose}>
      <p className="mb-2 text-xs uppercase tracking-wide text-slate-500">Jogo</p>
      <div className="mb-5 grid grid-cols-3 gap-2">
        {VERSUS_GAMES.map((game) => (
          <button
            key={game.key}
            type="button"
            disabled={!game.available}
            onClick={() => setTipo(game.key)}
            className={`flex flex-col items-center gap-1 rounded-xl border p-3 text-center text-xs transition disabled:cursor-not-allowed disabled:opacity-40 ${
              tipo === game.key ? "border-emerald-500 bg-emerald-500/10 text-emerald-300" : "border-slate-700 hover:border-slate-500"
            }`}
          >
            <span className="text-2xl">{game.icon}</span>
            <span>{game.name}</span>
            {!game.available && <span className="text-[10px] text-slate-500">em breve</span>}
          </button>
        ))}
      </div>

      <p className="mb-2 text-xs uppercase tracking-wide text-slate-500">Adversário</p>
      {candidates.length > 6 && (
        <input
          className="mb-2 w-full rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm outline-none focus:border-emerald-500"
          placeholder="Buscar colaborador"
          value={busca}
          onChange={(event) => setBusca(event.target.value)}
        />
      )}
      <div className="max-h-56 space-y-1 overflow-y-auto">
        {filtrados.map((pessoa) => (
          <button
            key={pessoa.id}
            type="button"
            onClick={() => setAdversario(pessoa.id)}
            className={`flex w-full items-center gap-3 rounded-lg p-2 text-left transition ${
              adversario === pessoa.id ? "bg-emerald-500/15 ring-1 ring-emerald-500" : "hover:bg-slate-800"
            }`}
          >
            <Avatar name={pessoa.name} url={pessoa.avatarUrl} online={pessoa.status === "ONLINE"} size="sm" />
            <span className="truncate text-sm">{pessoa.name}</span>
          </button>
        ))}
        {filtrados.length === 0 && <p className="p-2 text-sm text-slate-500">Ninguém encontrado.</p>}
      </div>

      <button
        type="button"
        disabled={!tipo || adversario === null || enviando}
        onClick={desafiar}
        className="mt-5 w-full rounded-lg bg-emerald-600 py-2 text-sm font-medium text-white transition hover:bg-emerald-500 disabled:opacity-50"
      >
        {enviando ? "Enviando..." : "Enviar desafio"}
      </button>
    </Modal>
  );
}
