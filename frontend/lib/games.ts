import type { Game, GamePlayer, GameType, SoloGameType } from "./types";

export interface GameInfo {
  key: string;
  name: string;
  icon: string;
  description: string;
  /** Jogos ainda não implementados aparecem como "em breve". */
  available: boolean;
  /** Como cada lado é chamado: quem desafiou (0) e o adversário (1). */
  sides: [string, string];
  /** Tem relógio (ritmo escolhido no desafio) e aceita proposta de empate. */
  timed: boolean;
}

/** Ritmos oferecidos: minutos de relógio para cada jogador. */
export const TIME_CONTROLS = [10, 5, 3];

/** Jogos de duas pessoas. `key` é o `GameType` do backend. */
export const VERSUS_GAMES: GameInfo[] = [
  { key: "TIC_TAC_TOE", name: "Jogo da velha", icon: "⭕", description: "Três em linha vence.", available: true, sides: ["X", "O"], timed: false },
  { key: "CHECKERS", name: "Damas", icon: "⛀", description: "Regras brasileiras: captura obrigatória e dama que voa.", available: true, sides: ["Brancas", "Pretas"], timed: true },
  { key: "CHESS", name: "Xadrez", icon: "♞", description: "Dê xeque-mate no rei adversário.", available: true, sides: ["Brancas", "Pretas"], timed: true },
];

export interface SoloMode {
  key: string;
  name: string;
}

export interface SoloGameInfo {
  key: SoloGameType;
  name: string;
  icon: string;
  description: string;
  available: boolean;
  modes: SoloMode[];
}

/** Jogos para jogar sozinho, com ranking de tempo entre os colaboradores. `key` é o `SoloGame` do backend. */
export const SOLO_GAMES: SoloGameInfo[] = [
  {
    key: "MINESWEEPER",
    name: "Campo minado",
    icon: "💣",
    description: "Abra tudo sem achar uma mina.",
    available: true,
    modes: [
      { key: "EASY", name: "Iniciante" },
      { key: "MEDIUM", name: "Intermediário" },
      { key: "HARD", name: "Especialista" },
    ],
  },
  {
    key: "SOLITAIRE",
    name: "Paciência",
    icon: "🃏",
    description: "Monte as quatro pilhas por naipe.",
    available: true,
    modes: [
      { key: "DRAW_ONE", name: "Virar 1 carta" },
      { key: "DRAW_THREE", name: "Virar 3 cartas" },
    ],
  },
];

/** Tempo de partida como `m:ss`, ou `h:mm:ss` a partir de uma hora. */
export function formatDuration(ms: number): string {
  const total = Math.floor(ms / 1000);
  const horas = Math.floor(total / 3600);
  const minutos = Math.floor((total % 3600) / 60);
  const segundos = String(total % 60).padStart(2, "0");
  return horas > 0 ? `${horas}:${String(minutos).padStart(2, "0")}:${segundos}` : `${minutos}:${segundos}`;
}

export function gameInfo(type: GameType | string): GameInfo {
  return (
    VERSUS_GAMES.find((game) => game.key === type) ?? {
      key: type,
      name: type,
      icon: "🎮",
      description: "",
      available: false,
      sides: ["Jogador 1", "Jogador 2"],
      timed: false,
    }
  );
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
    case "AGREED_DRAW":
      return "Empate combinado";
    case "TIMEOUT":
      return game.winnerId === meId ? `Tempo de ${outro} acabou — você venceu` : "Seu tempo acabou";
    case "DECLINED":
      return game.opponent.id === meId ? "Você recusou o desafio" : `${outro} recusou o desafio`;
    case "CANCELED":
      return "Desafio cancelado";
    default:
      return "Encerrada";
  }
}

/** Ritmo da partida em minutos, ou null se não tem relógio. */
export function timeControlMinutes(game: Game): number | null {
  return game.clock ? Math.round(game.clock.initialMs / 60_000) : null;
}

/** Tempo restante de um jogador agora, descontando o que passou desde que a partida chegou. */
export function timeLeft(game: Game, playerId: number, now: number): number | null {
  const clock = game.clock;
  if (!clock) return null;
  const base = playerId === game.challenger.id ? clock.challengerMs : clock.opponentMs;
  if (clock.runningPlayerId !== playerId || game.receivedAt === undefined) return base;
  return Math.max(0, base - (now - game.receivedAt));
}

/** Marca quando a partida chegou do servidor; o relógio na tela corre a partir daí. */
export function stampGame(game: Game): Game {
  return { ...game, receivedAt: clockNow() };
}

/** Mantém a versão mais nova de cada partida: o evento em tempo real e a resposta REST podem chegar em qualquer ordem. */
export function upsertGame(games: Game[], game: Game): Game[] {
  const atual = games.find((item) => item.id === game.id);
  if (!atual) return [game, ...games];
  if (Date.parse(game.updatedAt) < Date.parse(atual.updatedAt)) return games;
  return games.map((item) => (item.id === game.id ? game : item));
}

/**
 * Relógio dos cronômetros dos jogos solo. Só é chamado em handlers de evento; fica fora dos
 * componentes porque a regra `react-hooks/purity` não distingue handler de render.
 */
export function clockNow(): number {
  return Date.now();
}
