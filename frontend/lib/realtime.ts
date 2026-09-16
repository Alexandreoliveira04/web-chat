import { Client } from '@stomp/stompjs';
import { API_URL, tokenStorage } from './api';
import type { ChatEvent, Message, PresenceEvent, ReadReceipt } from './types';

export interface RealtimeHandlers {
  onConnect: (reconnected: boolean) => void;
  onMessage: (message: Message) => void;
  onMessageUpdate: (message: Message) => void;
  onRead: (receipt: ReadReceipt) => void;
  onChatEvent: (event: ChatEvent) => void;
  onPresence: (presence: PresenceEvent) => void;
  onError: (message: string) => void;
}

function brokerURL(): string {
  if (API_URL.startsWith("http")) {
    return API_URL.replace(/^http/, "ws").replace(/\/api\/v1$/, "/ws");
  }

  const protocolo = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocolo}://${window.location.host}/ws`;
}

export function connectRealtime(handlers: RealtimeHandlers): Client {
  let connectedBefore = false;

  const client = new Client({
    brokerURL: brokerURL(),
    connectHeaders: { Authorization: `Bearer ${tokenStorage.get() ?? ''}` },
    reconnectDelay: 5000,
    onConnect: () => {
      const parse = <T>(fn: (payload: T) => void) => (frame: { body: string }) => fn(JSON.parse(frame.body) as T);

      client.subscribe('/user/queue/messages', parse<Message>(handlers.onMessage));
      client.subscribe('/user/queue/message-updates', parse<Message>(handlers.onMessageUpdate));
      client.subscribe('/user/queue/read', parse<ReadReceipt>(handlers.onRead));
      client.subscribe('/user/queue/chats', parse<ChatEvent>(handlers.onChatEvent));
      client.subscribe('/topic/presence', parse<PresenceEvent>(handlers.onPresence));

      handlers.onConnect(connectedBefore);
      connectedBefore = true;
    },
    onStompError: (frame) => {
      const message = frame.headers.message ?? 'Erro na conexão em tempo real';
      if (message.includes('Autenticacao')) {
        client.deactivate();
        tokenStorage.clear();
        window.location.href = '/login';
        return;
      }
      handlers.onError(message);
    },
  });

  client.activate();
  return client;
}

export function sendOverWebSocket(client: Client | null, chatId: number, content: string): boolean {
  if (!client?.connected) {
    return false;
  }

  client.publish({ destination: `/app/chats/${chatId}/messages`, body: JSON.stringify({ content }) });
  return true;
}
