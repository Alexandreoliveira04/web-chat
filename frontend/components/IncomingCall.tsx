"use client";

import Avatar from "./Avatar";
import type { Participant } from "@/lib/types";

interface Props {
  caller: Participant | undefined;
  onAccept: () => void;
  onDecline: () => void;
}

export default function IncomingCall({ caller, onAccept, onDecline }: Props) {
  const nome = caller?.name ?? "Alguém";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="flex w-80 flex-col items-center gap-4 rounded-2xl border border-slate-700 bg-slate-900 p-8 shadow-2xl">
        <div className="rounded-full ring-4 ring-emerald-500/40 animate-pulse">
          <Avatar name={nome} url={caller?.avatarUrl} size="lg" />
        </div>
        <div className="text-center">
          <p className="text-lg font-semibold text-slate-100">{nome}</p>
          <p className="text-sm text-slate-400">está te ligando…</p>
        </div>
        <div className="mt-2 flex gap-6">
          <button
            type="button"
            onClick={onDecline}
            title="Recusar"
            className="flex h-14 w-14 items-center justify-center rounded-full bg-red-600 text-white transition hover:bg-red-500"
          >
            <PhoneIcon className="rotate-[135deg]" />
          </button>
          <button
            type="button"
            onClick={onAccept}
            title="Atender"
            className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-600 text-white transition hover:bg-emerald-500"
          >
            <PhoneIcon />
          </button>
        </div>
      </div>
    </div>
  );
}

export function PhoneIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>
  );
}
