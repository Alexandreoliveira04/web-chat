"use client";

import { useState } from "react";
import Modal from "./Modal";
import Avatar from "./Avatar";
import { contactsWithoutMe } from "@/lib/format";
import type { User } from "@/lib/types";

interface Props {
  me: User;
  users: User[];
  onClose: () => void;
  onCreate: (name: string, participantIds: number[]) => Promise<void>;
}

export default function NewGroupModal({ me, users, onClose, onCreate }: Props) {
  const [nome, setNome] = useState("");
  const [selecionados, setSelecionados] = useState<number[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  function alternar(id: number) {
    setSelecionados((atual) => (atual.includes(id) ? atual.filter((item) => item !== id) : [...atual, id]));
  }

  async function criar() {
    setErro(null);
    setSalvando(true);
    try {
      await onCreate(nome.trim(), selecionados);
      onClose();
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível criar o grupo");
    } finally {
      setSalvando(false);
    }
  }

  return (
    <Modal title="Novo grupo" onClose={onClose}>
      <div className="space-y-4">
        <input
          className="w-full rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 outline-none focus:border-emerald-500"
          placeholder="Nome do grupo"
          value={nome}
          maxLength={100}
          onChange={(event) => setNome(event.target.value)}
        />

        <div className="max-h-64 overflow-y-auto rounded-lg border border-slate-800">
          {contactsWithoutMe(users, me.id).map((user) => (
            <label
              key={user.id}
              className="flex cursor-pointer items-center gap-3 border-b border-slate-800/60 p-2 hover:bg-slate-800/60"
            >
              <input
                type="checkbox"
                className="accent-emerald-500"
                checked={selecionados.includes(user.id)}
                onChange={() => alternar(user.id)}
              />
              <Avatar name={user.name} online={user.status === "ONLINE"} size="sm" />
              <span className="text-sm">{user.name}</span>
            </label>
          ))}
        </div>

        {erro && <p className="text-sm text-red-400">{erro}</p>}

        <button
          type="button"
          disabled={salvando || !nome.trim() || selecionados.length === 0}
          onClick={criar}
          className="w-full rounded-lg bg-emerald-600 py-2 font-medium text-white transition hover:bg-emerald-500 disabled:opacity-50"
        >
          Criar grupo com {selecionados.length + 1} participantes
        </button>
      </div>
    </Modal>
  );
}
