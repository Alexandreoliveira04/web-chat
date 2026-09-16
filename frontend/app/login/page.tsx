"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { authApi } from "@/lib/api";

type Mode = "login" | "register";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setAviso(null);
    setCarregando(true);

    try {
      if (mode === "login") {
        await authApi.login(email, password);
        router.replace("/chat");
        return;
      }

      await authApi.register(name, email, password);
      setMode("login");
      setPassword("");
      setAviso("Conta criada. Faça login para continuar.");
    } catch (erro) {
      setError(erro instanceof Error ? erro.message : "Erro inesperado");
    } finally {
      setCarregando(false);
    }
  }

  return (
    <main className="flex h-full items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-2xl border border-slate-800 bg-slate-900 p-8 shadow-xl">
        <h1 className="text-center text-2xl font-semibold text-emerald-400">Web Chat</h1>
        <p className="mt-1 text-center text-sm text-slate-400">
          {mode === "login" ? "Entre para conversar" : "Crie sua conta"}
        </p>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          {mode === "register" && (
            <Field label="Nome" id="name">
              <input
                id="name"
                className={inputClass}
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                minLength={2}
                maxLength={100}
              />
            </Field>
          )}

          <Field label="E-mail" id="email">
            <input
              id="email"
              type="email"
              className={inputClass}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </Field>

          <Field label="Senha" id="password">
            <input
              id="password"
              type="password"
              className={inputClass}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
              maxLength={100}
            />
          </Field>

          {error && <p className="text-sm text-red-400">{error}</p>}
          {aviso && <p className="text-sm text-emerald-400">{aviso}</p>}

          <button
            type="submit"
            disabled={carregando}
            className="w-full rounded-lg bg-emerald-600 py-2 font-medium text-white transition hover:bg-emerald-500 disabled:opacity-60"
          >
            {carregando ? "Aguarde..." : mode === "login" ? "Entrar" : "Cadastrar"}
          </button>
        </form>

        <button
          type="button"
          className="mt-6 w-full text-center text-sm text-slate-400 hover:text-slate-200"
          onClick={() => {
            setMode(mode === "login" ? "register" : "login");
            setError(null);
            setAviso(null);
          }}
        >
          {mode === "login" ? "Não tem conta? Cadastre-se" : "Já tem conta? Entrar"}
        </button>
      </div>
    </main>
  );
}

const inputClass =
  "w-full rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-slate-100 outline-none focus:border-emerald-500";

function Field({ label, id, children }: { label: string; id: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm text-slate-300">
        {label}
      </label>
      {children}
    </div>
  );
}
