"use client";

import {useState} from "react";
import Modal from "./Modal";
import Avatar from "./Avatar";
import {contactsWithoutMe} from "@/lib/format";
import type {User} from "@/lib/types";

interface Props {
    me: User;
    users: User[];
    onClose: () => void;
    onCreate: (name: string, participantIds: number[]) => Promise<void>;
}

export default function NewGroupModal({me, users, onClose, onCreate}: Props) {
    const [nome, setNome] = useState("");
    const [busca, setBusca] = useState("");
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
            setErro(falha instanceof Error ? falha.message : "N\u00e3o foi poss\u00edvel criar o grupo");
        } finally {
            setSalvando(false);
        }
    }

    const contatos = contactsWithoutMe(users, me.id);
    const filtrados = busca.trim()
        ? contatos.filter((u) => u.name.toLowerCase().includes(busca.trim().toLowerCase()))
        : contatos;

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

                <input
                    className="w-full rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm outline-none focus:border-indigo-500"
                    placeholder="Buscar colaborador..."
                    value={busca}
                    onChange={(event) => setBusca(event.target.value)}
                />

                <div
                    className="max-h-64 min-h-[150px] overflow-y-auto rounded-lg border border-slate-800 bg-slate-900/50">
                    {filtrados.map((user) => (
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
                            <Avatar name={user.name} online={user.status === "ONLINE"} size="sm"/>
                            <span className="text-sm">{user.name}</span>
                        </label>
                    ))}
                    {filtrados.length === 0 && (
                        <p className="p-4 text-center text-sm text-slate-500">Nenhum colaborador encontrado.</p>
                    )}
                </div>

                {erro && <p className="text-sm text-red-400">{erro}</p>}

                <button
                    type="button"
                    disabled={salvando || !nome.trim() || selecionados.length === 0}
                    onClick={criar}
                    className="w-full rounded-lg bg-emerald-600 py-2 font-medium text-white transition hover:bg-emerald-500 disabled:opacity-50"
                >
                    {selecionados.length > 0
                        ? `Criar grupo com ${selecionados.length + 1} participantes`
                        : "Criar grupo"}
                </button>
            </div>
        </Modal>
    );
}
