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
    return <main className="flex h-full items-center justify-center text-slate-400">Carregando...</main>;
  }

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
        <section className="flex flex-1 items-center justify-center text-slate-500">
          Selecione uma conversa para começar
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
