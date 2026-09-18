"use client";

import { useState } from "react";
import { formatTime } from "@/lib/format";
import type { Chat, Message, Participant } from "@/lib/types";

interface Props {
  message: Message;
  chat: Chat;
  meId: number;
  autor?: Participant;
  onEdit: (content: string) => Promise<void>;
  onDelete: () => Promise<void>;
}

export default function MessageItem({ message, chat, meId, autor, onEdit, onDelete }: Props) {
  const [editando, setEditando] = useState(false);
  const [texto, setTexto] = useState(message.content);

  const minha = message.senderId === meId;
  const apagada = Boolean(message.deletedAt);
  const lida = chat.lastReadByOthersMessageId !== null && chat.lastReadByOthersMessageId >= message.id;

  async function salvar() {
    const novo = texto.trim();
    if (!novo || novo === message.content) {
      setEditando(false);
      setTexto(message.content);
      return;
    }
    await onEdit(novo);
    setEditando(false);
  }

  return (
    <div className={`group flex ${minha ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[70%] rounded-2xl px-4 py-2 ${minha ? "bg-emerald-800/80" : "bg-slate-800"} ${
          apagada ? "italic text-slate-400" : ""
        }`}
      >
        {chat.type === "GROUP" && !minha && (
          <p className="mb-1 text-xs font-semibold text-emerald-400">{autor?.name ?? "Desconhecido"}</p>
        )}

        {editando ? (
          <div className="space-y-2">
            <textarea
              className="w-full rounded-lg border border-slate-600 bg-slate-900 p-2 text-sm outline-none focus:border-emerald-500"
              value={texto}
              maxLength={2000}
              rows={2}
              onChange={(event) => setTexto(event.target.value)}
              autoFocus
            />
            <div className="flex justify-end gap-2 text-xs">
              <button
                type="button"
                className="text-slate-400 hover:text-slate-200"
                onClick={() => {
                  setEditando(false);
                  setTexto(message.content);
                }}
              >
                Cancelar
              </button>
              <button type="button" className="text-emerald-400 hover:text-emerald-300" onClick={salvar}>
                Salvar
              </button>
            </div>
          </div>
        ) : (
          <p className="whitespace-pre-wrap break-words text-sm">
            {apagada 
              ? "mensagem apagada" 
              : message.content.split(/(\p{Extended_Pictographic})/gu).map((part, index) => {
                  if (/\p{Extended_Pictographic}/u.test(part)) {
                    return <span key={index} className="emoji-icon">{part}</span>;
                  }
                  return part;
                })
            }
          </p>
        )}

        <div className="mt-1 flex items-center justify-end gap-2 text-[11px] text-slate-400">
          {message.editedAt && !apagada && <span>editada</span>}
          <span>{formatTime(message.createdAt)}</span>
          {minha && !apagada && <span title={lida ? "Lida" : "Enviada"}>{lida ? "✓✓" : "✓"}</span>}
          {minha && !apagada && !editando && (
            <span className="hidden gap-2 group-hover:flex">
              <button type="button" className="hover:text-slate-200" onClick={() => setEditando(true)}>
                editar
              </button>
              <button type="button" className="hover:text-red-400" onClick={onDelete}>
                apagar
              </button>
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
