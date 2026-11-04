"use client";

import { useState } from "react";
import type { User } from "@/lib/types";
import { usersApi } from "@/lib/api";
import Modal from "./Modal";
import AvatarPicker from "./AvatarPicker";

interface Props {
  me: User;
  onClose: () => void;
  onUpdate: (updated: User) => void;
}

export default function ProfileModal({ me, onClose, onUpdate }: Props) {
  const [name, setName] = useState(me.name);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    if (!name.trim()) {
      setError("O nome não pode ficar vazio.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      // A foto é salva na hora, pelo AvatarPicker; aqui só o nome.
      if (name.trim() !== me.name) {
        onUpdate(await usersApi.update(name.trim()));
      }
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro ao atualizar perfil");
      setLoading(false);
    }
  }

  function handleDeleteAccount() {
    const firstConfirm = confirm("ATENÇÃO: Você tem certeza que deseja EXCLUIR sua conta?\n(Lembre-se: O backend atual não possui rota de exclusão no MVP.)");
    if (firstConfirm) {
      const secondConfirm = prompt("Para confirmar a exclusão, digite a palavra 'EXCLUIR':");
      if (secondConfirm === 'EXCLUIR') {
        alert("Sua conta foi removida com sucesso. (Simulação Frontend)");
      } else if (secondConfirm !== null) {
        alert("Palavra incorreta. A exclusão foi cancelada.");
      }
    }
  }

  return (
    <Modal title="Editar Perfil" onClose={onClose}>
      <div className="flex flex-col items-center gap-6 pb-2">
        <AvatarPicker
          name={name}
          url={me.avatarUrl}
          onSave={async (foto) => onUpdate(await usersApi.uploadAvatar(foto))}
        />

        <div className="w-full">
          <label className="mb-2 block text-sm font-medium text-slate-300">
            Nome / Apelido
          </label>
          <input
            type="text"
            className="w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 outline-none focus:border-indigo-500"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        {error && <p className="text-sm text-red-500">{error}</p>}

        <div className="flex w-full gap-3 mt-2">
          <button
            type="button"
            onClick={handleDeleteAccount}
            className="rounded-lg border border-red-500/50 px-4 py-3 text-sm font-medium text-red-400 transition hover:bg-red-500/10"
          >
            Excluir Conta
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={loading}
            className="flex-1 rounded-lg bg-indigo-600 px-4 py-3 font-medium text-white transition hover:bg-indigo-700 disabled:opacity-50"
          >
            {loading ? "Salvando..." : "Salvar Alterações"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
