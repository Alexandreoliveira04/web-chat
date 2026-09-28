"use client";

import { useState } from "react";
import Avatar from "./Avatar";
import { chatSubtitle, chatTitle, otherParticipant } from "@/lib/format";
import type { Chat, Message } from "@/lib/types";

interface Props {
  chat: Chat;
  meId: number;
  messages: Message[];
  onClose: () => void;
}

export default function ChatDetailsSidebar({ chat, meId, messages, onClose }: Props) {
  const [viewingImageIndex, setViewingImageIndex] = useState<number | null>(null);
  const [viewingVideoIndex, setViewingVideoIndex] = useState<number | null>(null);

  const mediaMessages = messages.filter(
    (m) => !m.deletedAt && m.content.match(/^http.*?\.(jpg|jpeg|png|gif|webp)(?:\?.*)?$/i)
  );

  const videoMessages = messages.filter(
    (m) => !m.deletedAt && m.content.match(/^http.*?\.(mp4|webm|ogg|mov)(?:\?.*)?$/i)
  );

  const linkMessages = messages.filter(
    (m) =>
      !m.deletedAt &&
      !m.content.match(/^http.*?\.(jpg|jpeg|png|gif|webp)(?:\?.*)?$/i) &&
      !m.content.match(/^http.*?\.(mp4|webm|ogg|mov)(?:\?.*)?$/i) &&
      m.content.match(/https?:\/\/[^\s]+/i)
  );

  const extractLinks = (text: string) => {
    const urls = text.match(/https?:\/\/[^\s]+/gi);
    return urls || [];
  };

  const isGroup = chat.type === "GROUP";
  const otherUser = !isGroup ? otherParticipant(chat, meId) : null;

  return (
    <>
      <aside className="flex w-80 shrink-0 flex-col border-l border-slate-800 bg-slate-900 overflow-y-auto">
        <header className="flex items-center gap-4 border-b border-slate-800 p-4">
          <button onClick={onClose} className="text-slate-400 hover:text-slate-200 transition">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
          <h2 className="font-medium text-slate-200">Dados do contato</h2>
        </header>

        <div className="flex flex-col items-center border-b border-slate-800 p-6 text-center">
          <Avatar name={chatTitle(chat, meId)} size="lg" />
          <h3 className="mt-4 text-lg font-medium text-slate-200">{chatTitle(chat, meId)}</h3>
          <p className="text-sm text-slate-400">{chatSubtitle(chat, meId)}</p>
          
          {otherUser && (
            <div className="mt-4 w-full rounded-lg bg-slate-800 p-3 text-left">
              <p className="text-xs text-slate-500">Email</p>
              <p className="text-sm text-slate-300">{otherUser.email}</p>
            </div>
          )}
        </div>

        <div className="p-4">
          <h4 className="mb-3 text-sm font-semibold uppercase tracking-wider text-slate-500">Mídia, links e docs</h4>
          
          {mediaMessages.length === 0 && videoMessages.length === 0 && linkMessages.length === 0 && (
            <p className="text-sm text-slate-500">Nenhum anexo compartilhado.</p>
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
            <div className="mb-6">
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

          {linkMessages.length > 0 && (
            <div>
              <p className="mb-2 text-xs text-slate-400">Links ({linkMessages.length})</p>
              <div className="space-y-2">
                {linkMessages.map((msg) => (
                  <div key={msg.id} className="rounded-md bg-slate-800 p-2 text-sm">
                    {extractLinks(msg.content).map((link, i) => (
                      <a key={i} href={link} target="_blank" rel="noreferrer" className="block truncate text-emerald-400 hover:underline">
                        {link}
                      </a>
                    ))}
                  </div>
                ))}
              </div>
            </div>
          )}
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
