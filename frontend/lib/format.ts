import type { Chat, Message, Participant, User } from "./types";

export function otherParticipant(chat: Chat, meId: number): Participant | undefined {
  return chat.participants.find((participant) => participant.id !== meId);
}

export function chatTitle(chat: Chat, meId: number): string {
  if (chat.type === "GROUP") {
    return chat.name ?? "Grupo";
  }
  return otherParticipant(chat, meId)?.name ?? "Conversa";
}

export function chatSubtitle(chat: Chat, meId: number): string {
  if (chat.type === "GROUP") {
    return `${chat.participants.length} participantes`;
  }
  return otherParticipant(chat, meId)?.status === "ONLINE" ? "online" : "offline";
}

export function isOnline(chat: Chat, meId: number): boolean {
  return chat.type === "DIRECT" && otherParticipant(chat, meId)?.status === "ONLINE";
}

export function messagePreview(message: Message | null, meId: number, participants: Participant[]): string {
  if (!message) {
    return "Nenhuma mensagem ainda";
  }

  const autor =
    message.senderId === meId
      ? "Você: "
      : participants.length > 2
        ? `${participants.find((p) => p.id === message.senderId)?.name.split(" ")[0] ?? ""}: `
        : "";

  return message.deletedAt ? `${autor}mensagem apagada` : `${autor}${message.content}`;
}

export function initials(name: string): string {
  const partes = name.trim().split(/\s+/);
  return (partes[0]?.[0] ?? "?").concat(partes.length > 1 ? partes[partes.length - 1][0] : "").toUpperCase();
}

export function formatTime(iso: string): string {
  const data = new Date(iso);
  const hora = data.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });

  if (data.toDateString() === new Date().toDateString()) {
    return hora;
  }
  return `${data.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })} ${hora}`;
}

export function sortChats(chats: Chat[]): Chat[] {
  return [...chats].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt) || b.id - a.id);
}

export function contactsWithoutMe(users: User[], meId: number): User[] {
  return users.filter((user) => user.id !== meId).sort((a, b) => a.name.localeCompare(b.name));
}
