export type UserStatus = 'ONLINE' | 'OFFLINE';
export type Role = 'USER' | 'ADMIN';
export type ChatType = 'DIRECT' | 'GROUP';

export interface User {
  id: number;
  name: string;
  email: string;
  status: UserStatus;
  role: Role;
  avatarUrl?: string;
}

export interface Participant {
  id: number;
  name: string;
  email: string;
  status: UserStatus;
  avatarUrl?: string;
}

export interface Message {
  id: number;
  chatId: number;
  senderId: number;
  content: string;
  createdAt: string;
  editedAt: string | null;
  deletedAt: string | null;
}

export interface Chat {
  id: number;
  type: ChatType;
  name: string | null;
  avatarUrl: string | null;
  ownerId: number | null;
  participants: Participant[];
  lastMessage: Message | null;
  unreadCount: number;
  lastReadByOthersMessageId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface MessageHistory {
  messages: Message[];
  hasMore: boolean;
  nextBefore: number | null;
}

export interface ReadReceipt {
  chatId: number;
  readerId: number;
  lastReadMessageId: number;
  markedAsRead: number;
}

export interface PresenceEvent {
  userId: number;
  status: UserStatus;
}

export interface ProfileEvent {
  id: number;
  name: string;
  avatarUrl: string | null;
}

export interface ChatEvent {
  event: 'CREATED' | 'UPDATED' | 'REMOVED';
  chatId: number;
}

export type CallEndReason = 'CANCELED' | 'DECLINED' | 'HUNG_UP' | 'BUSY' | 'UNAVAILABLE' | 'DISCONNECTED';

export interface CallEvent {
  type: 'RINGING' | 'ACCEPTED' | 'ENDED';
  chatId: number;
  callerId: number;
  calleeId: number;
  reason: CallEndReason | null;
}

export type SignalType = 'OFFER' | 'ANSWER' | 'ICE';

export interface CallSignal {
  chatId: number;
  fromId: number;
  type: SignalType;
  data: unknown;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  fields?: Record<string, string>;
}

export type GameType = 'TIC_TAC_TOE';
export type GameStatus = 'INVITED' | 'IN_PROGRESS' | 'FINISHED';
export type GameResult = 'WIN' | 'DRAW' | 'RESIGNATION' | 'DECLINED' | 'CANCELED';

export interface GamePlayer {
  id: number;
  name: string;
  avatarUrl: string | null;
}

/** Jogo da velha: 9 casas linha por linha, `X`, `O` ou `.` (vazia). Quem desafiou é o X. */
export interface TicTacToeState {
  board: string;
  toMove: 0 | 1;
}

/** Partida entre dois colaboradores. `challenger` é o jogador 0; `opponent`, o 1. */
export interface Game {
  id: number;
  type: GameType;
  status: GameStatus;
  result: GameResult | null;
  chatId: number | null;
  challenger: GamePlayer;
  opponent: GamePlayer;
  turnPlayerId: number | null;
  winnerId: number | null;
  state: unknown;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
}
