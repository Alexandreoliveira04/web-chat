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

export type GameType = 'TIC_TAC_TOE' | 'CHECKERS' | 'CHESS';
export type GameStatus = 'INVITED' | 'IN_PROGRESS' | 'FINISHED';
export type GameResult = 'WIN' | 'DRAW' | 'AGREED_DRAW' | 'RESIGNATION' | 'TIMEOUT' | 'DECLINED' | 'CANCELED';

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

/**
 * Damas: 64 casas linha por linha a partir do canto superior esquerdo. `w`/`W` pedra/dama
 * branca (de quem desafiou, começa embaixo), `b`/`B` preta, `.` vazia. `moves` são as
 * jogadas legais já calculadas pelo servidor, cada uma como o caminho de casas.
 */
export interface CheckersState {
  board: string;
  toMove: 0 | 1;
  quietMoves: number;
  moves: number[][];
  lastMove: number[];
  lastCaptured: number[];
}

export type ChessEnding = 'CHECKMATE' | 'STALEMATE' | 'REPETITION' | 'INSUFFICIENT_MATERIAL' | 'FIFTY_MOVES';

/** Xadrez: quem desafiou joga de brancas. Jogadas em UCI (`e2e4`, `e7e8q`). */
export interface ChessState {
  fen: string;
  toMove: 0 | 1;
  history: string[];
  san: string[];
  moves: string[];
  check: boolean;
  ending: ChessEnding | null;
}

/**
 * Relógio no momento em que o servidor montou a resposta. Do lado de `runningPlayerId`, o
 * tempo continua correndo: desconte o que passou desde `Game.receivedAt`.
 */
export interface GameClock {
  initialMs: number;
  challengerMs: number;
  opponentMs: number;
  runningPlayerId: number | null;
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
  /** Só xadrez e damas; nulo no jogo da velha. */
  clock: GameClock | null;
  /** Quem propôs empate e espera resposta. */
  drawOfferedBy: number | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  /** Só no navegador: quando a partida chegou, para o relógio continuar correndo. */
  receivedAt?: number;
}

export type SoloGameType = 'MINESWEEPER' | 'SOLITAIRE';

export interface SoloRankingEntry {
  position: number;
  player: GamePlayer;
  bestDurationMs: number;
  wins: number;
}

export interface SoloRanking {
  game: SoloGameType;
  mode: string;
  top: SoloRankingEntry[];
  me: SoloRankingEntry | null;
}

export interface SoloResult {
  durationMs: number;
  personalBest: boolean;
  ranking: SoloRanking;
}
