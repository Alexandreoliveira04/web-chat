"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { tokenStorage } from "@/lib/api";

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    router.replace(tokenStorage.get() ? "/chat" : "/login");
  }, [router]);

  return <main className="flex h-full items-center justify-center text-slate-400">Carregando...</main>;
}
