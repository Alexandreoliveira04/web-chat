/** Regras do campo minado. Funções puras: cada jogada devolve um tabuleiro novo. */

export type MinesweeperLevel = "EASY" | "MEDIUM" | "HARD";

export const LEVELS: Record<MinesweeperLevel, { rows: number; cols: number; mines: number }> = {
  EASY: { rows: 9, cols: 9, mines: 10 },
  MEDIUM: { rows: 16, cols: 16, mines: 40 },
  HARD: { rows: 16, cols: 30, mines: 99 },
};

export interface Cell {
  mine: boolean;
  adjacent: number;
  revealed: boolean;
  flagged: boolean;
}

export type MinesweeperStatus = "ready" | "playing" | "won" | "lost";

export interface MinesweeperBoard {
  rows: number;
  cols: number;
  mines: number;
  cells: Cell[];
  status: MinesweeperStatus;
  /** Casa da mina que explodiu, para destacar. */
  exploded: number | null;
}

export function newBoard(level: MinesweeperLevel): MinesweeperBoard {
  const { rows, cols, mines } = LEVELS[level];
  return {
    rows,
    cols,
    mines,
    cells: Array.from({ length: rows * cols }, () => ({ mine: false, adjacent: 0, revealed: false, flagged: false })),
    status: "ready",
    exploded: null,
  };
}

export function neighbors(index: number, rows: number, cols: number): number[] {
  const row = Math.floor(index / cols);
  const col = index % cols;
  const result: number[] = [];
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) continue;
      const r = row + dr;
      const c = col + dc;
      if (r >= 0 && r < rows && c >= 0 && c < cols) result.push(r * cols + c);
    }
  }
  return result;
}

/**
 * Sorteia as minas só no primeiro clique, longe da casa clicada e das vizinhas: o primeiro
 * clique nunca perde e sempre abre uma área.
 */
export function placeMines(board: MinesweeperBoard, safe: number, random: () => number = Math.random): MinesweeperBoard {
  const proibidas = new Set([safe, ...neighbors(safe, board.rows, board.cols)]);
  let livres = board.cells.map((_, i) => i).filter((i) => !proibidas.has(i));
  // Em tabuleiro muito cheio pode faltar espaço; aí só a casa clicada fica protegida.
  if (livres.length < board.mines) livres = board.cells.map((_, i) => i).filter((i) => i !== safe);

  for (let i = livres.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [livres[i], livres[j]] = [livres[j], livres[i]];
  }
  const minas = new Set(livres.slice(0, board.mines));

  const cells = board.cells.map((cell, i) => ({ ...cell, mine: minas.has(i) }));
  cells.forEach((cell, i) => {
    cell.adjacent = neighbors(i, board.rows, board.cols).filter((n) => cells[n].mine).length;
  });

  return { ...board, cells, status: "playing" };
}

/** Abre a casa; casa sem minas em volta abre as vizinhas em cascata. */
export function reveal(board: MinesweeperBoard, index: number, random?: () => number): MinesweeperBoard {
  if (board.status === "won" || board.status === "lost") return board;

  const atual = board.status === "ready" ? placeMines(board, index, random) : board;
  const alvo = atual.cells[index];
  if (alvo.revealed || alvo.flagged) return atual;

  const cells = atual.cells.map((cell) => ({ ...cell }));

  if (cells[index].mine) {
    cells.forEach((cell) => {
      if (cell.mine) cell.revealed = true;
    });
    return { ...atual, cells, status: "lost", exploded: index };
  }

  const fila = [index];
  while (fila.length) {
    const i = fila.pop()!;
    const cell = cells[i];
    if (cell.revealed || cell.flagged) continue;
    cell.revealed = true;
    if (cell.adjacent === 0) {
      neighbors(i, atual.rows, atual.cols).forEach((n) => {
        if (!cells[n].revealed && !cells[n].mine) fila.push(n);
      });
    }
  }

  return checkWin({ ...atual, cells });
}

export function toggleFlag(board: MinesweeperBoard, index: number): MinesweeperBoard {
  if (board.status !== "playing" && board.status !== "ready") return board;
  const cell = board.cells[index];
  if (cell.revealed) return board;

  const cells = board.cells.map((item, i) => (i === index ? { ...item, flagged: !item.flagged } : item));
  return { ...board, cells };
}

/**
 * Clique num número já aberto com a quantidade certa de bandeiras em volta: abre todas as
 * vizinhas sem bandeira. Bandeira errada faz perder, como no jogo original.
 */
export function chord(board: MinesweeperBoard, index: number): MinesweeperBoard {
  const cell = board.cells[index];
  if (board.status !== "playing" || !cell.revealed || cell.adjacent === 0) return board;

  const vizinhas = neighbors(index, board.rows, board.cols);
  const bandeiras = vizinhas.filter((n) => board.cells[n].flagged).length;
  if (bandeiras !== cell.adjacent) return board;

  return vizinhas.reduce(
    (atual, n) => (atual.cells[n].flagged || atual.cells[n].revealed ? atual : reveal(atual, n)),
    board,
  );
}

export function flagsLeft(board: MinesweeperBoard): number {
  return board.mines - board.cells.filter((cell) => cell.flagged).length;
}

function checkWin(board: MinesweeperBoard): MinesweeperBoard {
  const venceu = board.cells.every((cell) => cell.mine || cell.revealed);
  if (!venceu) return board;
  return { ...board, status: "won", cells: board.cells.map((cell) => (cell.mine ? { ...cell, flagged: true } : cell)) };
}
