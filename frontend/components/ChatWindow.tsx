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
  const [busca, setBusca] = useState("");
  const fim = useRef<HTMLDivElement>(null);
  const ultimaMensagem = messages[messages.length - 1]?.id;

  useEffect(() => {
    fim.current?.scrollIntoView({ block: "end" });
  }, [chat.id, ultimaMensagem]);

  const [mostrarEmoji, setMostrarEmoji] = useState(false);
  const emojiContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    import("emoji-picker-element");

    function handleClickOutside(event: MouseEvent) {
      if (emojiContainerRef.current && !emojiContainerRef.current.contains(event.target as Node)) {
        setMostrarEmoji(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  useEffect(() => {
    const picker = emojiContainerRef.current?.querySelector("emoji-picker");
    if (picker) {
      const onEmojiClick = (e: any) => {
        setTexto((t) => t + e.detail.unicode);
        setMostrarEmoji(false);
      };
      picker.addEventListener("emoji-click", onEmojiClick);
      return () => picker.removeEventListener("emoji-click", onEmojiClick);
    }
  }, [mostrarEmoji]);

  async function enviar(event: FormEvent) {
    event.preventDefault();
    const conteudo = texto.trim();
    if (!conteudo) return;

    setTexto("");
    setMostrarEmoji(false);
    await onSend(conteudo);
  }

  const matches = busca.trim() 
    ? messages.filter(m => m.content.toLowerCase().includes(busca.trim().toLowerCase()) && !m.deletedAt)
    : [];
  const [currentMatch, setCurrentMatch] = useState(0);

  useEffect(() => {
    if (matches.length > 0) {
      const match = matches[currentMatch] || matches[0];
      const element = document.getElementById(`msg-${match.id}`);
      if (element) {
        element.scrollIntoView({ behavior: "smooth", block: "center" });
        // piscar background para destacar
        element.classList.add("bg-indigo-500/30");
        setTimeout(() => element.classList.remove("bg-indigo-500/30"), 1000);
      }
    }
  }, [busca, currentMatch]);

  function nextMatch() {
    if (matches.length > 0) setCurrentMatch((curr) => (curr + 1) % matches.length);
  }
  function prevMatch() {
    if (matches.length > 0) setCurrentMatch((curr) => (curr - 1 + matches.length) % matches.length);
  }

  const bgSvg = encodeURIComponent(`
<svg width='400' height='400' viewBox='0 0 400 400' xmlns='http://www.w3.org/2000/svg'>
  <g fill='#94a3b8' fill-opacity='0.08' font-family='monospace' font-weight='bold' transform='rotate(-15 200 200)'>
    <text x='50' y='50' font-size='16'>{ }</text>
    <text x='150' y='80' font-size='14'>&lt;/&gt;</text>
    <text x='250' y='60' font-size='16'>=&gt;</text>
    <text x='350' y='100' font-size='18'>();</text>
    <text x='80' y='160' font-size='14'>/* */</text>
    <text x='180' y='200' font-size='16'>||</text>
    <text x='280' y='150' font-size='16'>&amp;&amp;</text>
    <text x='40' y='260' font-size='18'>[ ]</text>
    <text x='140' y='320' font-size='14'>===</text>
    <text x='260' y='280' font-size='14'>!==</text>
    <text x='340' y='220' font-size='16'>$()</text>
    <text x='360' y='340' font-size='14'>&lt;!-- --&gt;</text>
    <text x='20' y='380' font-size='14'>++</text>
    <text x='100' y='40' font-size='12'>Java</text>
    <text x='200' y='30' font-size='10'>TypeScript</text>
    <text x='300' y='40' font-size='12'>Next.js</text>
    <text x='40' y='110' font-size='12'>Spring Boot</text>
    <text x='220' y='110' font-size='10'>PostgreSQL</text>
    <text x='320' y='140' font-size='12'>React</text>
    <text x='120' y='150' font-size='10'>Python</text>
    <text x='30' y='210' font-size='12'>Docker</text>
    <text x='240' y='220' font-size='10'>Tailwind</text>
    <text x='100' y='260' font-size='12'>Node.js</text>
    <text x='320' y='260' font-size='10'>Git</text>
    <text x='180' y='360' font-size='12'>Linux</text>
    <text x='280' y='380' font-size='10'>AWS</text>
    <text x='160' y='260' font-size='10'>sudo rm -rf /</text>
    <text x='60' y='330' font-size='10'>418 I'm a teapot</text>
    <text x='240' y='330' font-size='10'>It works on my machine</text>
  </g>
</svg>`.trim());

  return (
    <section className="flex flex-1 flex-col bg-slate-950">
      <header className="flex items-center gap-3 border-b border-slate-800 bg-slate-900 p-4">
        <Avatar name={chatTitle(chat, me.id)} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{chatTitle(chat, me.id)}</p>
          <p className="text-xs text-slate-400">{chatSubtitle(chat, me.id)}</p>
        </div>
        
        <div className="flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800 px-3 py-1">
          <input
            type="text"
            placeholder="Buscar mensagem..."
            value={busca}
            onChange={(e) => {
              setBusca(e.target.value);
              setCurrentMatch(0);
            }}
            className="w-40 bg-transparent text-sm outline-none focus:border-emerald-500"
          />
          {matches.length > 0 && (
            <div className="flex items-center gap-1 text-xs text-slate-400">
              <span>{currentMatch + 1}/{matches.length}</span>
              <button type="button" onClick={prevMatch} className="hover:text-slate-200">▲</button>
              <button type="button" onClick={nextMatch} className="hover:text-slate-200">▼</button>
            </div>
          )}
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

      <div 
        className="flex-1 space-y-3 overflow-y-auto p-4 chat-bg"
        style={{
          backgroundImage: `url("data:image/svg+xml,${bgSvg}")`,
          backgroundSize: '700px 700px'
        }}
      >
        {hasMore && (
          <div className="flex justify-center">
            <button
              type="button"
              onClick={onLoadOlder}
              disabled={carregando}
              className="rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-1 text-xs text-slate-300 hover:border-emerald-500 disabled:opacity-50"
            >
              {carregando ? "Carregando..." : "Carregar mensagens anteriores"}
            </button>
          </div>
        )}

        {messages.length === 0 && !carregando && (
          <p className="pt-10 text-center text-sm font-medium text-slate-400 drop-shadow-md">
            Nenhuma mensagem encontrada.
          </p>
        )}

        {messages.map((message) => (
          <div key={message.id} id={`msg-${message.id}`} className="transition-colors duration-500 rounded-2xl">
            <MessageItem
              message={message}
              chat={chat}
              meId={me.id}
              autor={chat.participants.find((participante) => participante.id === message.senderId)}
              onEdit={(content) => onEdit(message.id, content)}
              onDelete={() => onDelete(message.id)}
            />
          </div>
        ))}
        <div ref={fim} />
      </div>

      <form onSubmit={enviar} className="flex gap-2 border-t border-slate-800 bg-slate-900 p-4 relative">
        <div ref={emojiContainerRef} className="relative">
          <button
            type="button"
            onClick={() => setMostrarEmoji(!mostrarEmoji)}
            className="flex h-10 w-10 items-center justify-center rounded-lg border border-slate-700 bg-slate-800 text-slate-400 transition hover:border-emerald-500 hover:text-emerald-400"
          >
            <span className="emoji-icon">😊</span>
          </button>
          {mostrarEmoji && (
            <div className="absolute bottom-12 left-0 z-50 overflow-hidden rounded-lg shadow-xl" style={{ border: '1px solid #1e293b' }}>
              {/* @ts-ignore - Custom element n\u00e3o tipado no React */}
              <emoji-picker class="dark" style={{ '--background': '#0f172a', '--border-color': '#1e293b' }}></emoji-picker>
            </div>
          )}
        </div>
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
