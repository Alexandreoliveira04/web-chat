"use client";

import { useState } from "react";
import type { User } from "@/lib/types";
import Modal from "./Modal";
import Avatar from "./Avatar";

interface Props {
  me: User;
  onClose: () => void;
  onUpdate: (newName: string) => Promise<void>;
}

export default function ProfileModal({ me, onClose, onUpdate }: Props) {
  const [name, setName] = useState(me.name);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    if (!name.trim()) {
      setError("O nome n\u00e3o pode ficar vazio.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await onUpdate(name.trim());
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro ao atualizar perfil");
      setLoading(false);
    }
  }

  function handleDeleteAccount() {
    const firstConfirm = confirm("ATEN\u00c7\u00c3O: Voc\u00ea tem certeza que deseja EXCLUIR sua conta?\n(Lembre-se: O backend atual n\u00e3o possui rota de exclus\u00e3o no MVP.)");
    if (firstConfirm) {
      const secondConfirm = prompt("Para confirmar a exclus\u00e3o, digite a palavra 'EXCLUIR':");
      if (secondConfirm === 'EXCLUIR') {
        alert("Sua conta foi removida com sucesso. (Simula\u00e7\u00e3o Frontend)");
      } else if (secondConfirm !== null) {
        alert("Palavra incorreta. A exclus\u00e3o foi cancelada.");
      }
    }
  }

  return (
    <Modal title="Editar Perfil" onClose={onClose}>
      <div className="flex flex-col items-center gap-6 pb-2">
        <div className="flex flex-col items-center gap-2">
           <Avatar name={name} size="lg" />
        </div>

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
            {loading ? "Salvando..." : "Salvar Altera\u00e7\u00f5es"}
          </button>
        </div>
      </div>
    </Modal>
  );
}
