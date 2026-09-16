export type UserStatus = 'ONLINE' | 'OFFLINE';
export type Role = 'USER' | 'ADMIN';
export type ChatType = 'DIRECT' | 'GROUP';

export interface User {
  id: number;
  name: string;
  email: string;
  status: UserStatus;
  role: Role;
}

export interface Participant {
  id: number;
  name: string;
  email: string;
  status: UserStatus;
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

export interface ChatEvent {
  event: 'CREATED' | 'UPDATED' | 'REMOVED';
  chatId: number;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  fields?: Record<string, string>;
}
