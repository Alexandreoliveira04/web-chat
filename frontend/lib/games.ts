import type { Game, GamePlayer, GameType } from "./types";

export interface GameInfo {
  key: string;
  name: string;
  icon: string;
  description: string;
  /** Jogos ainda não implementados aparecem como "em breve". */
  available: boolean;
}

/** Jogos de duas pessoas. `key` é o `GameType` do backend. */
export const VERSUS_GAMES: GameInfo[] = [
  { key: "TIC_TAC_TOE", name: "Jogo da velha", icon: "⭕", description: "Três em linha vence.", available: true },
  { key: "CHECKERS", name: "Damas", icon: "⛀", description: "Capture todas as peças.", available: false },
  { key: "CHESS", name: "Xadrez", icon: "♞", description: "Dê xeque-mate no rei.", available: false },
];

/** Jogos para jogar sozinho, com ranking entre os colaboradores. */
export const SOLO_GAMES: GameInfo[] = [
  { key: "MINESWEEPER", name: "Campo minado", icon: "💣", description: "Abra tudo sem achar uma mina.", available: false },
  { key: "SOLITAIRE", name: "Paciência", icon: "🃏", description: "Monte as quatro pilhas por naipe.", available: false },
];

export function gameInfo(type: GameType | string): GameInfo {
  return VERSUS_GAMES.find((game) => game.key === type) ?? { key: type, name: type, icon: "🎮", description: "", available: false };
}

export function opponentOf(game: Game, meId: number): GamePlayer {
  return game.challenger.id === meId ? game.opponent : game.challenger;
}

export function isMyTurn(game: Game, meId: number): boolean {
  return game.status === "IN_PROGRESS" && game.turnPlayerId === meId;
}

/** Desafio recebido que ainda espera eu aceitar ou recusar. */
export function isInviteForMe(game: Game, meId: number): boolean {
  return game.status === "INVITED" && game.opponent.id === meId;
}

/** O que precisa da minha atenção: desafios recebidos e partidas em que é a minha vez. */
export function pendingGamesCount(games: Game[], meId: number): number {
  return games.filter((game) => isInviteForMe(game, meId) || isMyTurn(game, meId)).length;
}

function firstName(player: GamePlayer): string {
  return player.name.split(" ")[0];
}

export function statusLabel(game: Game, meId: number): string {
  const outro = firstName(opponentOf(game, meId));

  switch (game.status) {
    case "INVITED":
      return game.opponent.id === meId ? `${outro} te desafiou` : `Aguardando ${outro} aceitar`;
    case "IN_PROGRESS":
      return game.turnPlayerId === meId ? "Sua vez" : `Vez de ${outro}`;
  }

  switch (game.result) {
    case "WIN":
      return game.winnerId === meId ? "Você venceu!" : `${outro} venceu`;
    case "RESIGNATION":
      return game.winnerId === meId ? `${outro} desistiu — você venceu` : "Você desistiu";
    case "DRAW":
      return "Empate";
    case "DECLINED":
      return game.opponent.id === meId ? "Você recusou o desafio" : `${outro} recusou o desafio`;
    case "CANCELED":
      return "Desafio cancelado";
    default:
      return "Encerrada";
  }
}

/** Mantém a versão mais nova de cada partida: o evento em tempo real e a resposta REST podem chegar em qualquer ordem. */
export function upsertGame(games: Game[], game: Game): Game[] {
  const atual = games.find((item) => item.id === game.id);
  if (!atual) return [game, ...games];
  if (Date.parse(game.updatedAt) < Date.parse(atual.updatedAt)) return games;
  return games.map((item) => (item.id === game.id ? game : item));
}
