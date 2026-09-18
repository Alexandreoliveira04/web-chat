import type { ApiError, Chat, MessageHistory, Message, User } from './types';

// Sem NEXT_PUBLIC_API_URL, usa caminho relativo: em producao o proprio Spring Boot
// serve o front, entao API e front ficam na mesma origem. Em desenvolvimento, o
// .env.development aponta para o backend em outra porta.
export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? '/api/v1';

const TOKEN_KEY = 'webchat_token';

export const tokenStorage = {
  get: () => (typeof window === 'undefined' ? null : localStorage.getItem(TOKEN_KEY)),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export class RequestError extends Error {
  readonly status: number;
  readonly fields?: Record<string, string>;

  constructor(error: ApiError) {
    super(describe(error));
    this.status = error.status;
    this.fields = error.fields;
  }
}

function describe(error: ApiError): string {
  const fields = error.fields ? Object.values(error.fields).join(', ') : '';
  return fields ? `${error.message} (${fields})` : error.message;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokenStorage.get();

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (response.status === 401) {
    tokenStorage.clear();
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
    throw new RequestError({
      status: 401,
      error: 'Unauthorized',
      message: 'Sessão expirada. Entre novamente.',
      path,
    });
  }

  if (!response.ok) {
    let body: ApiError;
    try {
      body = (await response.json()) as ApiError;
    } catch {
      body = { status: response.status, error: '', message: 'Erro inesperado na requisição.', path };
    }
    throw new RequestError(body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

const body = (data: unknown) => JSON.stringify(data);

export const authApi = {
  login: async (email: string, password: string) => {
    const data = await request<{ token: string }>('/auth/login', {
      method: 'POST',
      body: body({ email, password }),
    });
    tokenStorage.set(data.token);
    return data;
  },
  register: (name: string, email: string, password: string) =>
    request<User>('/auth/register', { method: 'POST', body: body({ name, email, password }) }),
  logout: () => tokenStorage.clear(),
};

export const usersApi = {
  me: () => request<User>('/users/me'),
  list: () => request<User[]>('/users'),
  update: (name: string) => request<User>('/users/me', { method: 'PUT', body: body({ name }) }),
};

export const chatsApi = {
  list: () => request<Chat[]>('/chats'),
  byId: (chatId: number) => request<Chat>(`/chats/${chatId}`),
  openDirect: (participantId: number) =>
    request<Chat>('/chats', { method: 'POST', body: body({ participantId }) }),
  createGroup: (name: string, participantIds: number[]) =>
    request<Chat>('/chats/groups', { method: 'POST', body: body({ name, participantIds }) }),
  rename: (chatId: number, name: string) =>
    request<Chat>(`/chats/${chatId}`, { method: 'PATCH', body: body({ name }) }),
  addParticipant: (chatId: number, userId: number) =>
    request<Chat>(`/chats/${chatId}/participants`, { method: 'POST', body: body({ userId }) }),
  removeParticipant: (chatId: number, userId: number) =>
    request<Chat>(`/chats/${chatId}/participants/${userId}`, { method: 'DELETE' }),
  leave: (chatId: number) => request<void>(`/chats/${chatId}/participants/me`, { method: 'DELETE' }),
};

export const messagesApi = {
  history: (chatId: number, before?: number | null, size = 50) => {
    const params = new URLSearchParams({ size: String(size) });
    if (before) params.set('before', String(before));
    return request<MessageHistory>(`/chats/${chatId}/messages?${params}`);
  },
  send: (chatId: number, content: string) =>
    request<Message>(`/chats/${chatId}/messages`, { method: 'POST', body: body({ content }) }),
  edit: (chatId: number, messageId: number, content: string) =>
    request<Message>(`/chats/${chatId}/messages/${messageId}`, { method: 'PATCH', body: body({ content }) }),
  remove: (chatId: number, messageId: number) =>
    request<Message>(`/chats/${chatId}/messages/${messageId}`, { method: 'DELETE' }),
  markAsRead: (chatId: number) =>
    request<{ markedAsRead: number }>(`/chats/${chatId}/messages/read`, { method: 'PATCH' }),
};
