"use client";

import { useState } from "react";
import Modal from "./Modal";
import Avatar from "./Avatar";
import { contactsWithoutMe } from "@/lib/format";
import type { Chat, User } from "@/lib/types";

interface Props {
  chat: Chat;
  me: User;
  users: User[];
  onClose: () => void;
  onRename: (name: string) => Promise<void>;
  onAdd: (userId: number) => Promise<void>;
  onRemove: (userId: number) => Promise<void>;
  onLeave: () => Promise<void>;
}

export default function GroupSettingsModal({ chat, me, users, onClose, onRename, onAdd, onRemove, onLeave }: Props) {
  const [nome, setNome] = useState(chat.name ?? "");
  const [erro, setErro] = useState<string | null>(null);

  const souDono = chat.ownerId === me.id;
  const foraDoGrupo = contactsWithoutMe(users, me.id).filter(
    (user) => !chat.participants.some((participante) => participante.id === user.id),
  );

  async function executar(acao: () => Promise<void>) {
    setErro(null);
    try {
      await acao();
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível concluir a ação");
    }
  }

  return (
    <Modal title={chat.name ?? "Grupo"} onClose={onClose}>
      <div className="space-y-5">
        {souDono && (
          <div className="space-y-2">
            <p className="text-sm text-slate-300">Nome do grupo</p>
            <div className="flex gap-2">
              <input
                className="flex-1 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm outline-none focus:border-emerald-500"
                value={nome}
                maxLength={100}
                onChange={(event) => setNome(event.target.value)}
              />
              <button
                type="button"
                className="rounded-lg bg-emerald-600 px-3 text-sm text-white hover:bg-emerald-500"
                onClick={() => executar(() => onRename(nome.trim()))}
              >
                Salvar
              </button>
            </div>
          </div>
        )}

        <div className="space-y-2">
          <p className="text-sm text-slate-300">Participantes ({chat.participants.length})</p>
          <div className="max-h-48 space-y-1 overflow-y-auto">
            {chat.participants.map((participante) => (
              <div key={participante.id} className="flex items-center gap-3 rounded-lg p-2 hover:bg-slate-800/60">
                <Avatar name={participante.name} online={participante.status === "ONLINE"} size="sm" />
                <span className="flex-1 truncate text-sm">
                  {participante.name}
                  {chat.ownerId === participante.id && <span className="ml-2 text-xs text-emerald-400">dono</span>}
                </span>
                {souDono && participante.id !== chat.ownerId && (
                  <button
                    type="button"
                    className="text-xs text-slate-400 hover:text-red-400"
                    onClick={() => executar(() => onRemove(participante.id))}
                  >
                    remover
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>

        {souDono && foraDoGrupo.length > 0 && (
          <div className="space-y-2">
            <p className="text-sm text-slate-300">Adicionar participante</p>
            <select
              className="w-full rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm outline-none focus:border-emerald-500"
              defaultValue=""
              onChange={(event) => {
                const id = Number(event.target.value);
                event.target.value = "";
                if (id) executar(() => onAdd(id));
              }}
            >
              <option value="">Selecione...</option>
              {foraDoGrupo.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {erro && <p className="text-sm text-red-400">{erro}</p>}

        <button
          type="button"
          className="w-full rounded-lg border border-red-500/40 py-2 text-sm text-red-400 transition hover:bg-red-500/10"
          onClick={() => executar(onLeave)}
        >
          Sair do grupo
        </button>
      </div>
    </Modal>
  );
}
