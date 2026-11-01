"use client";

import { useEffect, useState } from "react";
import Avatar from "./Avatar";
import { PhoneIcon } from "./IncomingCall";
import type { CallState } from "@/lib/useVoiceCall";
import type { Participant } from "@/lib/types";

interface Props {
  call: CallState;
  peer: Participant | undefined;
  onOpenChat: () => void;
  onToggleMute: () => void;
  onHangUp: () => void;
}

/** Barra fixa no rodapé da lista de conversas, como o "Voz conectada" do Discord. */
export default function CallBar({ call, peer, onOpenChat, onToggleMute, onHangUp }: Props) {
  const agora = useAgora(call.startedAt !== null);
  const nome = peer?.name ?? "Chamada";

  let situacao = "Chamando…";
  let cor = "text-amber-400";
  if (call.status === "connecting") situacao = "Conectando…";
  if (call.status === "active" && call.startedAt !== null) {
    situacao = `Voz conectada · ${duracao(agora - call.startedAt)}`;
    cor = "text-emerald-400";
  }

  return (
    <div className="border-t border-slate-800 bg-slate-950/80 p-3">
      <div className="flex items-center gap-3">
        <button type="button" onClick={onOpenChat} className="flex min-w-0 flex-1 items-center gap-3 text-left">
          <Avatar name={nome} url={peer?.avatarUrl} size="sm" />
          <div className="min-w-0">
            <p className={`truncate text-xs font-semibold ${cor}`}>{situacao}</p>
            <p className="truncate text-sm text-slate-200">{nome}</p>
          </div>
        </button>

        <button
          type="button"
          onClick={onToggleMute}
          disabled={call.status === "outgoing"}
          title={call.muted ? "Ativar microfone" : "Silenciar microfone"}
          className={`flex h-9 w-9 items-center justify-center rounded-lg transition disabled:opacity-40 ${
            call.muted ? "bg-red-500/20 text-red-400 hover:bg-red-500/30" : "bg-slate-800 text-slate-300 hover:bg-slate-700"
          }`}
        >
          <MicIcon muted={call.muted} />
        </button>
        <button
          type="button"
          onClick={onHangUp}
          title="Desligar"
          className="flex h-9 w-9 items-center justify-center rounded-lg bg-red-600 text-white transition hover:bg-red-500"
        >
          <PhoneIcon className="rotate-[135deg]" />
        </button>
      </div>
    </div>
  );
}

function useAgora(ativo: boolean): number {
  const [agora, setAgora] = useState(() => Date.now());

  useEffect(() => {
    if (!ativo) return;
    const intervalo = setInterval(() => setAgora(Date.now()), 1000);
    return () => clearInterval(intervalo);
  }, [ativo]);

  return agora;
}

function duracao(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutos = Math.floor(total / 60);
  const segundos = String(total % 60).padStart(2, "0");
  return `${minutos}:${segundos}`;
}

function MicIcon({ muted }: { muted: boolean }) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
      <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
      <line x1="12" y1="19" x2="12" y2="23" />
      {muted && <line x1="2" y1="2" x2="22" y2="22" />}
    </svg>
  );
}
