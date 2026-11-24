/** Regras da paciência (Klondike). Funções puras: cada jogada devolve um estado novo, ou null se não vale. */

export type Suit = "♠" | "♥" | "♦" | "♣";
export const SUITS: Suit[] = ["♠", "♥", "♦", "♣"];
export const RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"];

export interface Card {
  id: number;
  suit: Suit;
  /** 1 (ás) a 13 (rei). */
  rank: number;
  faceUp: boolean;
}

export interface SolitaireState {
  stock: Card[];
  waste: Card[];
  foundations: Card[][];
  tableau: Card[][];
  drawCount: 1 | 3;
  moves: number;
}

export type Pile =
  | { kind: "waste" }
  | { kind: "foundation"; index: number }
  | { kind: "tableau"; index: number; cardIndex?: number };

export function isRed(card: Card): boolean {
  return card.suit === "♥" || card.suit === "♦";
}

export function rankLabel(card: Card): string {
  return RANKS[card.rank - 1];
}

/** Embaralha e distribui: 7 colunas com 1 a 7 cartas, só a última de cada virada. */
export function deal(drawCount: 1 | 3, random: () => number = Math.random): SolitaireState {
  const baralho: Card[] = SUITS.flatMap((suit, s) =>
    Array.from({ length: 13 }, (_, r) => ({ id: s * 13 + r, suit, rank: r + 1, faceUp: false })),
  );
  for (let i = baralho.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [baralho[i], baralho[j]] = [baralho[j], baralho[i]];
  }

  const tableau: Card[][] = [];
  let posicao = 0;
  for (let coluna = 0; coluna < 7; coluna++) {
    const cartas = baralho.slice(posicao, posicao + coluna + 1).map((carta, i) => ({ ...carta, faceUp: i === coluna }));
    tableau.push(cartas);
    posicao += coluna + 1;
  }

  return { stock: baralho.slice(posicao), waste: [], foundations: [[], [], [], []], tableau, drawCount, moves: 0 };
}

/** Vira cartas do monte para o descarte; com o monte vazio, o descarte volta para o monte. */
export function draw(state: SolitaireState): SolitaireState | null {
  if (state.stock.length === 0) {
    if (state.waste.length === 0) return null;
    const stock = [...state.waste].reverse().map((carta) => ({ ...carta, faceUp: false }));
    return { ...state, stock, waste: [], moves: state.moves + 1 };
  }

  const quantas = Math.min(state.drawCount, state.stock.length);
  const viradas = state.stock.slice(-quantas).reverse().map((carta) => ({ ...carta, faceUp: true }));
  return {
    ...state,
    stock: state.stock.slice(0, -quantas),
    waste: [...state.waste, ...viradas],
    moves: state.moves + 1,
  };
}

/** Cartas que saem de uma pilha: uma do descarte ou da fundação; da coluna, da carta escolhida até o fim. */
function cardsAt(state: SolitaireState, from: Pile): Card[] {
  if (from.kind === "waste") return state.waste.slice(-1);
  if (from.kind === "foundation") return state.foundations[from.index].slice(-1);

  const coluna = state.tableau[from.index];
  const inicio = from.cardIndex ?? coluna.length - 1;
  const cartas = coluna.slice(inicio);
  return cartas.length && cartas.every((carta) => carta.faceUp) ? cartas : [];
}

export function canStackOnTableau(card: Card, column: Card[]): boolean {
  const topo = column[column.length - 1];
  if (!topo) return card.rank === 13;
  return topo.faceUp && isRed(topo) !== isRed(card) && topo.rank === card.rank + 1;
}

export function canStackOnFoundation(card: Card, foundation: Card[]): boolean {
  const topo = foundation[foundation.length - 1];
  if (!topo) return card.rank === 1;
  return topo.suit === card.suit && topo.rank === card.rank - 1;
}

/** Move cartas entre pilhas; null se a jogada não vale. */
export function move(state: SolitaireState, from: Pile, to: Pile): SolitaireState | null {
  if (to.kind === "waste") return null;
  if (from.kind === to.kind && from.index === (to as { index?: number }).index) return null;

  const cartas = cardsAt(state, from);
  if (cartas.length === 0) return null;

  if (to.kind === "foundation") {
    if (cartas.length !== 1 || !canStackOnFoundation(cartas[0], state.foundations[to.index])) return null;
  } else if (!canStackOnTableau(cartas[0], state.tableau[to.index])) {
    return null;
  }

  const novo: SolitaireState = {
    ...state,
    waste: [...state.waste],
    foundations: state.foundations.map((pilha) => [...pilha]),
    tableau: state.tableau.map((coluna) => [...coluna]),
    moves: state.moves + 1,
  };

  if (from.kind === "waste") novo.waste.pop();
  else if (from.kind === "foundation") novo.foundations[from.index].pop();
  else {
    const coluna = novo.tableau[from.index];
    coluna.splice(coluna.length - cartas.length, cartas.length);
    const topo = coluna[coluna.length - 1];
    if (topo && !topo.faceUp) coluna[coluna.length - 1] = { ...topo, faceUp: true };
  }

  if (to.kind === "foundation") novo.foundations[to.index].push(cartas[0]);
  else novo.tableau[to.index].push(...cartas);

  return novo;
}

/** Manda a carta do topo para a primeira fundação onde ela cabe (o duplo clique). */
export function sendToFoundation(state: SolitaireState, from: Pile): SolitaireState | null {
  for (let index = 0; index < 4; index++) {
    const resultado = move(state, from, { kind: "foundation", index });
    if (resultado) return resultado;
  }
  return null;
}

export function isWon(state: SolitaireState): boolean {
  return state.foundations.every((pilha) => pilha.length === 13);
}

/** Com monte e descarte vazios e todas as cartas viradas, o jogo está ganho: dá para completar sozinho. */
export function canAutoComplete(state: SolitaireState): boolean {
  return (
    !isWon(state) &&
    state.stock.length === 0 &&
    state.waste.length === 0 &&
    state.tableau.every((coluna) => coluna.every((carta) => carta.faceUp))
  );
}

export function autoComplete(state: SolitaireState): SolitaireState {
  let atual = state;
  let mudou = true;
  while (mudou && !isWon(atual)) {
    mudou = false;
    for (let index = 0; index < 7; index++) {
      const proximo = sendToFoundation(atual, { kind: "tableau", index });
      if (proximo) {
        atual = proximo;
        mudou = true;
      }
    }
  }
  return atual;
}
