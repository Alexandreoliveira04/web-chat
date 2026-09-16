"use client";

import { useState, useRef, useEffect } from "react";
import Avatar from "./Avatar";
import ProfileModal from "./ProfileModal";
import { chatTitle, contactsWithoutMe, isOnline, messagePreview, sortChats } from "@/lib/format";
import type { Chat, User } from "@/lib/types";

interface Props {
  me: User;
  chats: Chat[];
  users: User[];
  activeChatId: number | null;
  conectado: boolean;
  onSelectChat: (chat: Chat) => void;
  onStartDirect: (user: User) => void;
  onNewGroup: () => void;
  onLogout: () => void;
  onUpdateMe?: (newName: string) => Promise<void>; // we'll need to pass this or use API directly
}

export default function Sidebar({
  me,
  chats,
  users,
  activeChatId,
  conectado,
  onSelectChat,
  onStartDirect,
  onNewGroup,
  onLogout,
  onUpdateMe,
}: Props) {
  const [busca, setBusca] = useState("");
  const [mostrarPerfil, setMostrarPerfil] = useState(false);
  const [perfilDropdown, setPerfilDropdown] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setPerfilDropdown(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const termo = busca.trim().toLowerCase();
  const conversas = sortChats(chats).filter((chat) => chatTitle(chat, me.id).toLowerCase().includes(termo));
  const semConversa = contactsWithoutMe(users, me.id)
    .filter((user) => !chats.some((chat) => chat.type === "DIRECT" && chat.participants.some((p) => p.id === user.id)))
    .filter((user) => user.name.toLowerCase().includes(termo));

  return (
    <aside className="flex w-80 shrink-0 flex-col border-r border-slate-800 bg-slate-900">
      <header className="relative flex items-center justify-between border-b border-slate-800 p-4">
        <button
          type="button"
          onClick={() => setPerfilDropdown(!perfilDropdown)}
          className="flex flex-1 items-center gap-3 text-left transition hover:opacity-80"
        >
          <Avatar name={me.name} online />
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">{me.name}</p>
            <p className="text-xs text-slate-400">{conectado ? "conectado" : "reconectando..."}</p>
          </div>
          <span className="mr-2 text-xs text-slate-500">▼</span>
        </button>

        {perfilDropdown && (
          <div
            ref={dropdownRef}
            className="absolute left-4 top-16 z-50 w-48 rounded-md border border-slate-700 bg-slate-800 py-1 shadow-lg"
          >
            <button
              onClick={() => {
                setPerfilDropdown(false);
                setMostrarPerfil(true);
              }}
              className="block w-full px-4 py-2 text-left text-sm text-slate-200 hover:bg-slate-700"
            >
              Editar Perfil
            </button>
            <button
              onClick={() => {
                document.documentElement.classList.toggle("theme-light");
                setPerfilDropdown(false);
              }}
              className="block w-full px-4 py-2 text-left text-sm text-slate-200 hover:bg-slate-700"
            >
              Alternar Tema
            </button>
            <button
              onClick={onLogout}
              className="block w-full px-4 py-2 text-left text-sm text-red-400 hover:bg-slate-700"
            >
              Sair
            </button>
          </div>
        )}
      </header>

      {mostrarPerfil && onUpdateMe && (
        <ProfileModal
          me={me}
          onClose={() => setMostrarPerfil(false)}
          onUpdate={onUpdateMe}
        />
      )}

      <div className="space-y-2 p-3">
        <input
          className="w-full rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm outline-none focus:border-emerald-500"
          placeholder="Buscar conversas e colaboradores"
          value={busca}
          onChange={(event) => setBusca(event.target.value)}
        />
        <button
          type="button"
          onClick={onNewGroup}
          className="w-full rounded-lg border border-slate-700 py-2 text-sm text-slate-200 transition hover:border-emerald-500 hover:text-emerald-400"
        >
          + Novo grupo
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {conversas.map((chat) => (
          <button
            key={chat.id}
            type="button"
            onClick={() => onSelectChat(chat)}
            className={`flex w-full items-center gap-3 border-b border-slate-800/60 p-3 text-left transition hover:bg-slate-800/60 ${
              activeChatId === chat.id ? "bg-slate-800" : ""
            }`}
          >
            <Avatar name={chatTitle(chat, me.id)} online={isOnline(chat, me.id)} />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">
                {chat.type === "GROUP" && <span className="mr-1 text-slate-400">#</span>}
                {chatTitle(chat, me.id)}
              </p>
              <p className="truncate text-xs text-slate-400">
                {messagePreview(chat.lastMessage, me.id, chat.participants)}
              </p>
            </div>
            {chat.unreadCount > 0 && (
              <span className="rounded-full bg-emerald-500 px-2 py-0.5 text-xs font-bold text-slate-900">
                {chat.unreadCount}
              </span>
            )}
          </button>
        ))}

        {semConversa.length > 0 && (
          <>
            <p className="px-3 pb-1 pt-4 text-xs uppercase tracking-wide text-slate-500">Colaboradores</p>
            {semConversa.map((user) => (
              <button
                key={user.id}
                type="button"
                onClick={() => onStartDirect(user)}
                className="flex w-full items-center gap-3 p-3 text-left transition hover:bg-slate-800/60"
              >
                <Avatar name={user.name} online={user.status === "ONLINE"} size="sm" />
                <div className="min-w-0">
                  <p className="truncate text-sm">{user.name}</p>
                  <p className="truncate text-xs text-slate-500">{user.email}</p>
                </div>
              </button>
            ))}
          </>
        )}

        {conversas.length === 0 && semConversa.length === 0 && (
          <p className="p-4 text-center text-sm text-slate-500">Nenhum resultado.</p>
        )}
      </div>
    </aside>
  );
}
