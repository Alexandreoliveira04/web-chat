"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import type { Client } from "@stomp/stompjs";
import Sidebar from "@/components/Sidebar";
import ChatWindow from "@/components/ChatWindow";
import NewGroupModal from "@/components/NewGroupModal";
import GroupSettingsModal from "@/components/GroupSettingsModal";
import { authApi, chatsApi, messagesApi, tokenStorage, usersApi } from "@/lib/api";
import { connectRealtime, sendOverWebSocket } from "@/lib/realtime";
import type { Chat, Message, User } from "@/lib/types";

export default function ChatPage() {
  const router = useRouter();

  const [me, setMe] = useState<User | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [chats, setChats] = useState<Chat[]>([]);
  const [activeChatId, setActiveChatId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [nextBefore, setNextBefore] = useState<number | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [conectado, setConectado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [mostrarNovoGrupo, setMostrarNovoGrupo] = useState(false);
  const [mostrarParticipantes, setMostrarParticipantes] = useState(false);

  const clientRef = useRef<Client | null>(null);
  const meRef = useRef<User | null>(null);
  const activeChatIdRef = useRef<number | null>(null);
  activeChatIdRef.current = activeChatId;

  const activeChat = chats.find((chat) => chat.id === activeChatId) ?? null;

  const carregarConversas = useCallback(async () => {
    const [listaUsuarios, listaChats] = await Promise.all([usersApi.list(), chatsApi.list()]);
    setUsers(listaUsuarios);
    setChats(listaChats);
    return listaChats;
  }, []);

  const marcarComoLida = useCallback(async (chatId: number) => {
    setChats((atual) => atual.map((chat) => (chat.id === chatId ? { ...chat, unreadCount: 0 } : chat)));
    try {
      await messagesApi.markAsRead(chatId);
    } catch {
      // se falhar, a próxima abertura da conversa tenta de novo
    }
  }, []);

  const abrirConversa = useCallback(
    async (chat: Chat) => {
      setActiveChatId(chat.id);
      activeChatIdRef.current = chat.id;
      setCarregando(true);
      setMessages([]);

      try {
        const historico = await messagesApi.history(chat.id);
        if (activeChatIdRef.current !== chat.id) return;

        setMessages(historico.messages);
        setHasMore(historico.hasMore);
        setNextBefore(historico.nextBefore);

        if (chat.unreadCount > 0) {
          await marcarComoLida(chat.id);
        }
      } catch (falha) {
        setErro(falha instanceof Error ? falha.message : "Não foi possível abrir a conversa");
      } finally {
        setCarregando(false);
      }
    },
    [marcarComoLida],
  );

  const recarregarConversa = useCallback(
    async (chatId: number) => {
      try {
        const chat = await chatsApi.byId(chatId);
        setChats((atual) =>
          atual.some((item) => item.id === chat.id)
            ? atual.map((item) => (item.id === chat.id ? chat : item))
            : [...atual, chat],
        );
      } catch {
        await carregarConversas();
      }
    },
    [carregarConversas],
  );

  useEffect(() => {
    if (!tokenStorage.get()) {
      router.replace("/login");
      return;
    }

    let ativo = true;

    (async () => {
      try {
        const usuario = await usersApi.me();
        if (!ativo) return;
        meRef.current = usuario;
        setMe(usuario);
        await carregarConversas();
      } catch (falha) {
        setErro(falha instanceof Error ? falha.message : "Não foi possível carregar o chat");
        return;
      }

      clientRef.current = connectRealtime({
        onConnect: (reconectado) => {
          setConectado(true);
          if (reconectado) {
            carregarConversas();
            const atual = activeChatIdRef.current;
            if (atual) {
              messagesApi.history(atual).then((historico) => {
                setMessages(historico.messages);
                setHasMore(historico.hasMore);
                setNextBefore(historico.nextBefore);
              });
            }
          }
        },
        onMessage: (message) => {
          const ativa = activeChatIdRef.current === message.chatId;

          if (ativa) {
            setMessages((atual) => (atual.some((item) => item.id === message.id) ? atual : [...atual, message]));
          }

          setChats((atual) => {
            const conhecida = atual.some((chat) => chat.id === message.chatId);
            if (!conhecida) {
              recarregarConversa(message.chatId);
              return atual;
            }

            return atual.map((chat) => {
              if (chat.id !== message.chatId) return chat;
              const minha = message.senderId === meRef.current?.id;
              return {
                ...chat,
                lastMessage: message,
                updatedAt: message.createdAt,
                unreadCount: ativa || minha ? chat.unreadCount : chat.unreadCount + 1,
              };
            });
          });

          if (ativa && !document.hidden) {
            marcarComoLida(message.chatId);
          }
        },
        onMessageUpdate: (message) => {
          setMessages((atual) => atual.map((item) => (item.id === message.id ? message : item)));
          setChats((atual) =>
            atual.map((chat) =>
              chat.id === message.chatId && chat.lastMessage?.id === message.id
                ? { ...chat, lastMessage: message }
                : chat,
            ),
          );
        },
        onRead: (receipt) => {
          setChats((atual) =>
            atual.map((chat) =>
              chat.id === receipt.chatId
                ? {
                    ...chat,
                    lastReadByOthersMessageId: receipt.lastReadMessageId,
                    unreadCount: receipt.readerId === meRef.current?.id ? 0 : chat.unreadCount,
                  }
                : chat,
            ),
          );
        },
        onChatEvent: (evento) => {
          if (evento.event === "REMOVED") {
            setChats((atual) => atual.filter((chat) => chat.id !== evento.chatId));
            if (activeChatIdRef.current === evento.chatId) {
              setActiveChatId(null);
              setMessages([]);
            }
            return;
          }
          recarregarConversa(evento.chatId);
        },
        onPresence: (presenca) => {
          setUsers((atual) =>
            atual.map((user) => (user.id === presenca.userId ? { ...user, status: presenca.status } : user)),
          );
          setChats((atual) =>
            atual.map((chat) => ({
              ...chat,
              participants: chat.participants.map((participante) =>
                participante.id === presenca.userId ? { ...participante, status: presenca.status } : participante,
              ),
            })),
          );
        },
        onError: (mensagem) => setErro(mensagem),
      });
    })();

    return () => {
      ativo = false;
      clientRef.current?.deactivate();
    };
  }, [router, carregarConversas, recarregarConversa, marcarComoLida]);

  useEffect(() => {
    function aoVoltar() {
      const atual = activeChatIdRef.current;
      if (!document.hidden && atual) {
        const chat = chats.find((item) => item.id === atual);
        if (chat && chat.unreadCount > 0) marcarComoLida(atual);
      }
    }

    document.addEventListener("visibilitychange", aoVoltar);
    return () => document.removeEventListener("visibilitychange", aoVoltar);
  }, [chats, marcarComoLida]);

  useEffect(() => {
    function onEscKeyDown(e: KeyboardEvent) {
      // Ignorar se houver modais abertos, ou se o foco estiver num input (opcional)
      if (e.key === "Escape" && !mostrarParticipantes && !mostrarNovoGrupo) {
        setActiveChatId(null);
        setMessages([]);
      }
    }
    window.addEventListener("keydown", onEscKeyDown);
    return () => window.removeEventListener("keydown", onEscKeyDown);
  }, [mostrarParticipantes, mostrarNovoGrupo]);

  async function iniciarConversaDireta(user: User) {
    try {
      const chat = await chatsApi.openDirect(user.id);
      setChats((atual) =>
        atual.some((item) => item.id === chat.id) ? atual.map((item) => (item.id === chat.id ? chat : item)) : [...atual, chat],
      );
      await abrirConversa(chat);
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível abrir a conversa");
    }
  }

  async function enviarMensagem(conteudo: string) {
    if (!activeChatId) return;

    if (sendOverWebSocket(clientRef.current, activeChatId, conteudo)) {
      return;
    }

    try {
      const message = await messagesApi.send(activeChatId, conteudo);
      setMessages((atual) => (atual.some((item) => item.id === message.id) ? atual : [...atual, message]));
      setChats((atual) =>
        atual.map((chat) =>
          chat.id === activeChatId ? { ...chat, lastMessage: message, updatedAt: message.createdAt } : chat,
        ),
      );
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível enviar a mensagem");
    }
  }

  async function carregarAnteriores() {
    if (!activeChatId || !nextBefore) return;

    setCarregando(true);
    try {
      const historico = await messagesApi.history(activeChatId, nextBefore);
      setMessages((atual) => [...historico.messages, ...atual]);
      setHasMore(historico.hasMore);
      setNextBefore(historico.nextBefore);
    } finally {
      setCarregando(false);
    }
  }

  async function executar(acao: () => Promise<unknown>) {
    try {
      await acao();
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : "Não foi possível concluir a ação");
      throw falha;
    }
  }

  if (!me) {
    return (
      <main className="flex h-full bg-slate-950">
        <div className="w-[320px] flex-shrink-0 border-r border-slate-800 bg-slate-950 flex flex-col">
          <div className="h-20 border-b border-slate-800 p-4 flex items-center gap-4">
            <div className="h-12 w-12 rounded-full bg-slate-800 animate-pulse"></div>
            <div className="flex-1 space-y-2">
              <div className="h-4 w-32 bg-slate-800 rounded animate-pulse"></div>
              <div className="h-3 w-16 bg-slate-800 rounded animate-pulse"></div>
            </div>
          </div>
          <div className="flex-1 p-4 space-y-6">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <div key={i} className="flex items-center gap-4">
                <div className="h-12 w-12 rounded-full bg-slate-800 animate-pulse"></div>
                <div className="flex-1 space-y-2">
                  <div className="h-4 w-3/4 bg-slate-800 rounded animate-pulse"></div>
                  <div className="h-3 w-1/2 bg-slate-800 rounded animate-pulse"></div>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="flex-1 flex flex-col bg-slate-950">
           <div className="h-20 border-b border-slate-800 p-4 flex items-center gap-4">
              <div className="h-12 w-12 rounded-full bg-slate-800 animate-pulse"></div>
              <div className="h-4 w-48 bg-slate-800 rounded animate-pulse"></div>
           </div>
           <div className="flex-1 p-8 space-y-8">
              <div className="flex gap-4">
                 <div className="h-24 w-72 bg-slate-800 rounded-2xl rounded-tl-none animate-pulse"></div>
              </div>
              <div className="flex justify-end gap-4">
                 <div className="h-16 w-64 bg-slate-800 rounded-2xl rounded-tr-none animate-pulse"></div>
              </div>
              <div className="flex gap-4">
                 <div className="h-32 w-96 bg-slate-800 rounded-2xl rounded-tl-none animate-pulse"></div>
              </div>
           </div>
           <div className="h-20 border-t border-slate-800 p-4 flex items-center">
              <div className="h-12 w-full bg-slate-800 rounded-lg animate-pulse"></div>
           </div>
        </div>
      </main>
    );
  }

  const bgStars = encodeURIComponent(`
<svg width="300" height="300" viewBox="0 0 300 300" xmlns="http://www.w3.org/2000/svg">
  <g fill="#ffffff" fill-opacity="0.15" font-family="monospace" font-weight="bold">
    <text x="25" y="35" font-size="14">*</text>
    <text x="150" y="50" font-size="10">.</text>
    <text x="260" y="80" font-size="12">+</text>
    <text x="85" y="130" font-size="14">;</text>
    <text x="200" y="160" font-size="12">:</text>
    <text x="45" y="190" font-size="10">.</text>
    <text x="125" y="90" font-size="16">*</text>
    <text x="280" y="190" font-size="12">~</text>
    <text x="230" y="250" font-size="14">;</text>
    <text x="75" y="275" font-size="12">+</text>
    <text x="160" y="280" font-size="10">.</text>
    <text x="285" y="40" font-size="14">*</text>
    <text x="25" y="230" font-size="12">:</text>
    
    <text x="110" y="210" font-size="14" fill-opacity="0.08">{ }</text>
    <text x="210" y="110" font-size="12" fill-opacity="0.08">&lt;/&gt;</text>
    <text x="50" y="100" font-size="10" fill-opacity="0.08">()</text>
    <text x="250" y="280" font-size="12" fill-opacity="0.08">[]</text>
  </g>
</svg>`.trim());

  return (
    <main className="flex h-full">
      <Sidebar
        me={me}
        chats={chats}
        users={users}
        activeChatId={activeChatId}
        conectado={conectado}
        onSelectChat={abrirConversa}
        onStartDirect={iniciarConversaDireta}
        onNewGroup={() => setMostrarNovoGrupo(true)}
        onLogout={() => {
          clientRef.current?.deactivate();
          authApi.logout();
          router.replace("/login");
        }}
        onUpdateMe={async (name) => {
          const updated = await usersApi.update(name);
          setMe(updated);
        }}
      />

      {activeChat ? (
        <ChatWindow
          chat={activeChat}
          me={me}
          messages={messages}
          hasMore={hasMore}
          carregando={carregando}
          onLoadOlder={carregarAnteriores}
          onSend={enviarMensagem}
          onEdit={(messageId, content) => executar(() => messagesApi.edit(activeChat.id, messageId, content))}
          onDelete={(messageId) => executar(() => messagesApi.remove(activeChat.id, messageId))}
          onOpenSettings={() => setMostrarParticipantes(true)}
        />
      ) : (
        <section 
          className="flex flex-1 flex-col items-center justify-center border-l border-slate-800 bg-slate-950 text-center"
          style={{ backgroundImage: `url("data:image/svg+xml,${bgStars}")`, backgroundSize: '300px 300px' }}
        >
          <div className="mb-4 flex items-center justify-center">
            <div className="relative h-64 w-64 rounded-full overflow-hidden border-4 border-slate-800 shadow-[0_0_30px_rgba(16,185,129,0.2)] animate-bounce-slow" style={{ animationDuration: '3s' }}>
              <img 
                src="/mascote.jpg" 
                alt="Mascote Astronauta" 
                className="h-full w-full object-cover scale-110"
              />
              <div className="absolute inset-0 rounded-full shadow-[inset_0_0_20px_rgba(0,0,0,0.8)]"></div>
            </div>
          </div>
          <h1 className="text-3xl font-light text-slate-200">Web Chat Desktop</h1>
          <p className="mt-4 max-w-md text-sm text-slate-400">
            Envie e receba mensagens em tempo real. <br />
            Selecione um colaborador ao lado para começar a conversar.
          </p>
          <div className="mt-12 flex items-center gap-2 text-xs text-slate-500">

          </div>
        </section>
      )}

      {erro && (
        <div className="fixed bottom-4 left-1/2 z-50 -translate-x-1/2 rounded-lg bg-red-500/90 px-4 py-2 text-sm text-white">
          {erro}
          <button type="button" className="ml-3 font-bold" onClick={() => setErro(null)}>
            ✕
          </button>
        </div>
      )}

      {mostrarNovoGrupo && (
        <NewGroupModal
          me={me}
          users={users}
          onClose={() => setMostrarNovoGrupo(false)}
          onCreate={async (nome, participantes) => {
            const grupo = await chatsApi.createGroup(nome, participantes);
            setChats((atual) => [...atual, grupo]);
            await abrirConversa(grupo);
          }}
        />
      )}

      {mostrarParticipantes && activeChat?.type === "GROUP" && (
        <GroupSettingsModal
          chat={activeChat}
          me={me}
          users={users}
          onClose={() => setMostrarParticipantes(false)}
          onRename={(nome) => executar(() => chatsApi.rename(activeChat.id, nome)).then(() => recarregarConversa(activeChat.id))}
          onAdd={(userId) => executar(() => chatsApi.addParticipant(activeChat.id, userId)).then(() => recarregarConversa(activeChat.id))}
          onRemove={(userId) =>
            executar(() => chatsApi.removeParticipant(activeChat.id, userId)).then(() => recarregarConversa(activeChat.id))
          }
          onLeave={() =>
            executar(() => chatsApi.leave(activeChat.id)).then(() => {
              setChats((atual) => atual.filter((chat) => chat.id !== activeChat.id));
              setActiveChatId(null);
              setMessages([]);
              setMostrarParticipantes(false);
            })
          }
        />
      )}
    </main>
  );
}
