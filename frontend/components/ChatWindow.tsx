"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import Avatar from "./Avatar";
import MessageItem from "./MessageItem";
import { chatSubtitle, chatTitle } from "@/lib/format";
import type { Chat, Message, User } from "@/lib/types";

interface Props {
  chat: Chat;
  me: User;
  messages: Message[];
  hasMore: boolean;
  carregando: boolean;
  onLoadOlder: () => void;
  onSend: (content: string) => Promise<void>;
  onEdit: (messageId: number, content: string) => Promise<void>;
  onDelete: (messageId: number) => Promise<void>;
  onOpenSettings: () => void;
}

export default function ChatWindow({
  chat,
  me,
  messages,
  hasMore,
  carregando,
  onLoadOlder,
  onSend,
  onEdit,
  onDelete,
  onOpenSettings,
}: Props) {
  const [texto, setTexto] = useState("");
  const fim = useRef<HTMLDivElement>(null);
  const ultimaMensagem = messages[messages.length - 1]?.id;

  useEffect(() => {
    fim.current?.scrollIntoView({ block: "end" });
  }, [chat.id, ultimaMensagem]);

  async function enviar(event: FormEvent) {
    event.preventDefault();
    const conteudo = texto.trim();
    if (!conteudo) return;

    setTexto("");
    await onSend(conteudo);
  }

  return (
    <section className="flex flex-1 flex-col bg-slate-950">
      <header className="flex items-center gap-3 border-b border-slate-800 bg-slate-900 p-4">
        <Avatar name={chatTitle(chat, me.id)} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{chatTitle(chat, me.id)}</p>
          <p className="text-xs text-slate-400">{chatSubtitle(chat, me.id)}</p>
        </div>
        {chat.type === "GROUP" && (
          <button
            type="button"
            onClick={onOpenSettings}
            className="rounded-lg border border-slate-700 px-3 py-1 text-sm text-slate-300 hover:border-emerald-500 hover:text-emerald-400"
          >
            Participantes
          </button>
        )}
      </header>

      <div className="flex-1 space-y-3 overflow-y-auto p-4">
        {hasMore && (
          <div className="flex justify-center">
            <button
              type="button"
              onClick={onLoadOlder}
              disabled={carregando}
              className="rounded-lg border border-slate-700 px-3 py-1 text-xs text-slate-300 hover:border-emerald-500 disabled:opacity-50"
            >
              {carregando ? "Carregando..." : "Carregar mensagens anteriores"}
            </button>
          </div>
        )}

        {messages.length === 0 && !carregando && (
          <p className="pt-10 text-center text-sm text-slate-500">
            Nenhuma mensagem ainda. Diga oi para {chatTitle(chat, me.id)}!
          </p>
        )}

        {messages.map((message) => (
          <MessageItem
            key={message.id}
            message={message}
            chat={chat}
            meId={me.id}
            autor={chat.participants.find((participante) => participante.id === message.senderId)}
            onEdit={(content) => onEdit(message.id, content)}
            onDelete={() => onDelete(message.id)}
          />
        ))}
        <div ref={fim} />
      </div>

      <form onSubmit={enviar} className="flex gap-2 border-t border-slate-800 bg-slate-900 p-4">
        <input
          className="flex-1 rounded-lg border border-slate-700 bg-slate-800 px-4 py-2 outline-none focus:border-emerald-500"
          placeholder="Mensagem"
          value={texto}
          maxLength={2000}
          onChange={(event) => setTexto(event.target.value)}
        />
        <button
          type="submit"
          className="rounded-lg bg-emerald-600 px-5 font-medium text-white transition hover:bg-emerald-500"
        >
          Enviar
        </button>
      </form>
    </section>
  );
}
