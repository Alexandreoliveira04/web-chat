"use client";

import { useEffect, useMemo, useState } from "react";
import Avatar from "./Avatar";
import DocumentCard from "./DocumentCard";
import { attachmentOf, linksIn } from "@/lib/attachments";
import { messagesApi } from "@/lib/api";
import { chatAvatarUrl, chatSubtitle, chatTitle, otherParticipant } from "@/lib/format";
import type { Chat, Message } from "@/lib/types";

interface Props {
  chat: Chat;
  meId: number;
  messages: Message[];
  onClose: () => void;
}

type Aba = "midia" | "docs" | "links";

export default function ChatDetailsSidebar({ chat, meId, messages, onClose }: Props) {
  const [viewingImageIndex, setViewingImageIndex] = useState<number | null>(null);
  const [viewingVideoIndex, setViewingVideoIndex] = useState<number | null>(null);
  const [aba, setAba] = useState<Aba>("midia");
  const [compartilhadas, setCompartilhadas] = useState<Message[]>([]);

  // O histórico do chat é paginado; a galeria busca no servidor tudo o que já foi compartilhado.
  useEffect(() => {
    let ativo = true;
    messagesApi
      .shared(chat.id)
      .then((lista) => ativo && setCompartilhadas(lista))
      .catch(() => {});
    return () => {
      ativo = false;
    };
  }, [chat.id]);

  // As mensagens já carregadas no chat têm a versão mais recente (novas, editadas, apagadas).
  const todas = useMemo(() => {
    const porId = new Map<number, Message>();
    compartilhadas.forEach((message) => porId.set(message.id, message));
    messages.forEach((message) => porId.set(message.id, message));
    return [...porId.values()].filter((message) => !message.deletedAt).sort((a, b) => b.id - a.id);
  }, [compartilhadas, messages]);

  const anexos = todas.map((message) => ({ message, anexo: attachmentOf(message.content) }));
  const mediaMessages = anexos.filter(({ anexo }) => anexo?.kind === "image").map(({ message }) => message);
  const videoMessages = anexos.filter(({ anexo }) => anexo?.kind === "video").map(({ message }) => message);
  const documentos = anexos.flatMap(({ message, anexo }) =>
    anexo?.kind === "document" ? [{ id: message.id, anexo }] : [],
  );
  const linkMessages = anexos
    .filter(({ anexo, message }) => !anexo && linksIn(message.content).length > 0)
    .map(({ message }) => message);

  const isGroup = chat.type === "GROUP";
  const otherUser = !isGroup ? otherParticipant(chat, meId) : null;

  const abas: { id: Aba; rotulo: string; total: number }[] = [
    { id: "midia", rotulo: "Mídia", total: mediaMessages.length + videoMessages.length },
    { id: "docs", rotulo: "Docs", total: documentos.length },
    { id: "links", rotulo: "Links", total: linkMessages.length },
  ];

  return (
    <>
      {/* Abaixo de 1280px o painel abre por cima do chat, com fundo escurecido; acima, fica ao lado. */}
      <div className="fixed inset-0 z-40 bg-black/50 xl:hidden" onClick={onClose} />

      <aside className="fixed inset-y-0 right-0 z-50 flex w-full max-w-sm flex-col border-l border-slate-800 bg-slate-900 shadow-2xl xl:static xl:z-auto xl:w-80 xl:max-w-none xl:shrink-0 xl:shadow-none">
        <header className="flex shrink-0 items-center gap-4 border-b border-slate-800 p-4">
          <button onClick={onClose} title="Fechar" className="text-slate-400 hover:text-slate-200 transition">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
          <h2 className="font-medium text-slate-200">{isGroup ? "Dados do grupo" : "Dados do contato"}</h2>
        </header>

        <div className="flex-1 overflow-y-auto">
          <div className="flex flex-col items-center border-b border-slate-800 p-6 text-center">
            <Avatar name={chatTitle(chat, meId)} url={chatAvatarUrl(chat, meId)} size="lg" />
            <h3 className="mt-4 text-lg font-medium text-slate-200">{chatTitle(chat, meId)}</h3>
            <p className="text-sm text-slate-400">{chatSubtitle(chat, meId)}</p>

            {otherUser && (
              <div className="mt-4 w-full rounded-lg bg-slate-800 p-3 text-left">
                <p className="text-xs text-slate-500">Email</p>
                <p className="truncate text-sm text-slate-300">{otherUser.email}</p>
              </div>
            )}
          </div>

          <div className="sticky top-0 z-10 flex border-b border-slate-800 bg-slate-900">
            {abas.map(({ id, rotulo, total }) => (
              <button
                key={id}
                type="button"
                onClick={() => setAba(id)}
                className={`flex-1 border-b-2 px-2 py-3 text-sm transition ${
                  aba === id ? "border-emerald-500 text-emerald-400" : "border-transparent text-slate-400 hover:text-slate-200"
                }`}
              >
                {rotulo} <span className="text-xs text-slate-500">({total})</span>
              </button>
            ))}
          </div>

          <div className="p-4">
            {aba === "midia" && (
              <>
                {mediaMessages.length === 0 && videoMessages.length === 0 && (
                  <p className="text-sm text-slate-500">Nenhuma foto ou vídeo compartilhado.</p>
                )}

                {mediaMessages.length > 0 && (
                  <div className="mb-6">
                    <p className="mb-2 text-xs text-slate-400">Imagens ({mediaMessages.length})</p>
                    <div className="grid grid-cols-3 gap-2">
                      {mediaMessages.map((msg, index) => (
                        <button
                          key={msg.id}
                          onClick={() => setViewingImageIndex(index)}
                          className="block aspect-square overflow-hidden rounded-md bg-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                        >
                          <img src={msg.content} alt="Mídia" className="h-full w-full object-cover transition hover:opacity-80" />
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {videoMessages.length > 0 && (
                  <div>
                    <p className="mb-2 text-xs text-slate-400">Vídeos ({videoMessages.length})</p>
                    <div className="grid grid-cols-3 gap-2">
                      {videoMessages.map((msg, index) => (
                        <button
                          key={msg.id}
                          onClick={() => setViewingVideoIndex(index)}
                          className="relative block aspect-square overflow-hidden rounded-md bg-slate-950 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                        >
                          <video src={msg.content} className="h-full w-full object-cover opacity-60 transition hover:opacity-100" />
                          <div className="absolute inset-0 flex items-center justify-center">
                            <svg className="h-8 w-8 text-white drop-shadow-md" fill="currentColor" viewBox="0 0 20 20"><path d="M4 4l12 6-12 6z" /></svg>
                          </div>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </>
            )}

            {aba === "docs" && (
              <>
                {documentos.length === 0 && <p className="text-sm text-slate-500">Nenhum documento compartilhado.</p>}
                <div className="space-y-2">
                  {documentos.map(({ id, anexo }) => (
                    <DocumentCard key={id} attachment={anexo} compact />
                  ))}
                </div>
              </>
            )}

            {aba === "links" && (
              <>
                {linkMessages.length === 0 && <p className="text-sm text-slate-500">Nenhum link compartilhado.</p>}
                <div className="space-y-2">
                  {linkMessages.map((msg) => (
                    <div key={msg.id} className="rounded-md bg-slate-800 p-2 text-sm">
                      {linksIn(msg.content).map((link, i) => (
                        <a key={i} href={link} target="_blank" rel="noreferrer" className="block truncate text-emerald-400 hover:underline">
                          {link}
                        </a>
                      ))}
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </aside>

      {/* Modal de visualizacao de imagem */}
      {viewingImageIndex !== null && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/95 p-4 backdrop-blur-sm">
          <button 
            onClick={() => setViewingImageIndex(null)}
            className="absolute left-6 top-6 flex items-center gap-2 text-slate-300 transition hover:text-white"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
            <span className="text-lg font-medium">Voltar</span>
          </button>
          {mediaMessages.length > 1 && (
            <button 
              onClick={() => setViewingImageIndex((prev) => (prev! > 0 ? prev! - 1 : mediaMessages.length - 1))}
              className="absolute left-6 rounded-full bg-slate-800/50 p-3 text-white backdrop-blur-md transition hover:bg-slate-700 focus:outline-none"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
            </button>
          )}
          <img 
            src={mediaMessages[viewingImageIndex].content} 
            className="max-h-[90vh] max-w-[90vw] rounded-lg object-contain shadow-2xl" 
            alt="Visualizacao expandida" 
          />
          {mediaMessages.length > 1 && (
            <button 
              onClick={() => setViewingImageIndex((prev) => (prev! < mediaMessages.length - 1 ? prev! + 1 : 0))}
              className="absolute right-6 rounded-full bg-slate-800/50 p-3 text-white backdrop-blur-md transition hover:bg-slate-700 focus:outline-none"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
            </button>
          )}
          <div className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-full bg-slate-800/50 px-4 py-1 text-sm font-medium text-white backdrop-blur-md">
            {viewingImageIndex + 1} de {mediaMessages.length}
          </div>
        </div>
      )}

      {/* Modal de visualizacao de video */}
      {viewingVideoIndex !== null && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/95 p-4 backdrop-blur-sm">
          <button 
            onClick={() => setViewingVideoIndex(null)}
            className="absolute left-6 top-6 flex items-center gap-2 text-slate-300 transition hover:text-white"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
            <span className="text-lg font-medium">Voltar</span>
          </button>
          {videoMessages.length > 1 && (
            <button 
              onClick={() => setViewingVideoIndex((prev) => (prev! > 0 ? prev! - 1 : videoMessages.length - 1))}
              className="absolute left-6 rounded-full bg-slate-800/50 p-3 text-white backdrop-blur-md transition hover:bg-slate-700 focus:outline-none z-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
            </button>
          )}
          <video 
            src={videoMessages[viewingVideoIndex].content} 
            controls
            autoPlay
            className="max-h-[90vh] max-w-[90vw] rounded-lg shadow-2xl outline-none" 
          />
          {videoMessages.length > 1 && (
            <button 
              onClick={() => setViewingVideoIndex((prev) => (prev! < videoMessages.length - 1 ? prev! + 1 : 0))}
              className="absolute right-6 rounded-full bg-slate-800/50 p-3 text-white backdrop-blur-md transition hover:bg-slate-700 focus:outline-none z-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
            </button>
          )}
          <div className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-full bg-slate-800/50 px-4 py-1 text-sm font-medium text-white backdrop-blur-md">
            {viewingVideoIndex + 1} de {videoMessages.length}
          </div>
        </div>
      )}
    </>
  );
}
