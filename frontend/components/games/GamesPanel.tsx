"use client";

import Avatar from "../Avatar";
import GameView from "./GameView";
import { SOLO_GAMES, VERSUS_GAMES, type GameInfo, gameInfo, isInviteForMe, isMyTurn, opponentOf, statusLabel } from "@/lib/games";
import type { Game, User } from "@/lib/types";

export interface GameActions {
  move: (game: Game, move: unknown) => Promise<void>;
  accept: (game: Game) => Promise<void>;
  decline: (game: Game) => Promise<void>;
  cancel: (game: Game) => Promise<void>;
  resign: (game: Game) => Promise<void>;
  rematch: (game: Game) => Promise<void>;
}

interface Props {
  me: User;
  games: Game[];
  openGameId: number | null;
  actions: GameActions;
  onOpenGame: (gameId: number | null) => void;
  onNewChallenge: (type?: string) => void;
  onOpenChat: (chatId: number) => void;
  onBack: () => void;
}

/** Área de jogos: lobby com desafios e partidas, ou a partida aberta. */
export default function GamesPanel({ me, games, openGameId, actions, onOpenGame, onNewChallenge, onOpenChat, onBack }: Props) {
  const aberta = games.find((game) => game.id === openGameId) ?? null;

  const recebidos = games.filter((game) => isInviteForMe(game, me.id));
  const emAndamento = games.filter((game) => game.status !== "FINISHED" && !isInviteForMe(game, me.id));
  const encerradas = games.filter((game) => game.status === "FINISHED");

  return (
    <section className="flex min-w-0 flex-1 flex-col bg-slate-950">
      <header className="flex items-center gap-3 border-b border-slate-800 bg-slate-900 p-3 md:p-4">
        <button
          type="button"
          onClick={aberta ? () => onOpenGame(null) : onBack}
          title={aberta ? "Voltar para os jogos" : "Fechar"}
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-300 hover:bg-slate-800"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
        </button>
        <div className="min-w-0 flex-1">
          <p className="font-medium">{aberta ? `${gameInfo(aberta.type).name} com ${opponentOf(aberta, me.id).name}` : "Jogos"}</p>
          <p className="text-xs text-slate-400">{aberta ? "Partida" : "Desafie colaboradores ou jogue sozinho"}</p>
        </div>
      </header>

      <div className="flex-1 overflow-y-auto">
        {aberta ? (
          <GameView
            key={aberta.id}
            game={aberta}
            me={me}
            onMove={(move) => actions.move(aberta, move)}
            onAccept={() => actions.accept(aberta)}
            onDecline={() => actions.decline(aberta)}
            onCancel={() => actions.cancel(aberta)}
            onResign={() => actions.resign(aberta)}
            onRematch={() => actions.rematch(aberta)}
            onOpenChat={aberta.chatId ? () => onOpenChat(aberta.chatId!) : undefined}
          />
        ) : (
          <div className="mx-auto w-full max-w-3xl space-y-8 p-4 md:p-8">
            <Secao titulo="Desafiar alguém">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {VERSUS_GAMES.map((info) => (
                  <CartaoDeJogo key={info.key} info={info} onClick={() => onNewChallenge(info.key)} />
                ))}
              </div>
            </Secao>

            <Secao titulo="Jogar sozinho">
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                {SOLO_GAMES.map((info) => (
                  <CartaoDeJogo key={info.key} info={info} onClick={() => undefined} />
                ))}
              </div>
            </Secao>

            {recebidos.length > 0 && (
              <Secao titulo="Desafios recebidos">
                {recebidos.map((game) => (
                  <LinhaDePartida key={game.id} game={game} me={me} destaque onClick={() => onOpenGame(game.id)} />
                ))}
              </Secao>
            )}

            <Secao titulo="Em andamento">
              {emAndamento.length === 0 && <p className="text-sm text-slate-500">Nenhuma partida em andamento.</p>}
              {emAndamento.map((game) => (
                <LinhaDePartida key={game.id} game={game} me={me} destaque={isMyTurn(game, me.id)} onClick={() => onOpenGame(game.id)} />
              ))}
            </Secao>

            {encerradas.length > 0 && (
              <Secao titulo="Encerradas">
                {encerradas.map((game) => (
                  <LinhaDePartida key={game.id} game={game} me={me} onClick={() => onOpenGame(game.id)} />
                ))}
              </Secao>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

function Secao({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-3 text-xs uppercase tracking-wide text-slate-500">{titulo}</h2>
      <div className="space-y-2">{children}</div>
    </section>
  );
}

function CartaoDeJogo({ info, onClick }: { info: GameInfo; onClick: () => void }) {
  const { name, icon, description, available } = info;
  return (
    <button
      type="button"
      disabled={!available}
      onClick={onClick}
      className="flex flex-col items-start gap-1 rounded-xl border border-slate-800 bg-slate-900 p-4 text-left transition hover:border-emerald-500 disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-slate-800"
    >
      <span className="text-3xl">{icon}</span>
      <span className="font-medium">{name}</span>
      <span className="text-xs text-slate-400">{available ? description : "Em breve"}</span>
    </button>
  );
}

function LinhaDePartida({ game, me, destaque = false, onClick }: { game: Game; me: User; destaque?: boolean; onClick: () => void }) {
  const outro = opponentOf(game, me.id);
  const info = gameInfo(game.type);

  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center gap-3 rounded-xl border p-3 text-left transition hover:bg-slate-800/60 ${
        destaque ? "border-emerald-500/60 bg-emerald-500/5" : "border-slate-800 bg-slate-900"
      }`}
    >
      <Avatar name={outro.name} url={outro.avatarUrl ?? undefined} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{outro.name}</p>
        <p className="truncate text-xs text-slate-400">
          {info.icon} {info.name}
        </p>
      </div>
      <span className={`shrink-0 text-xs ${destaque ? "font-semibold text-emerald-400" : "text-slate-400"}`}>
        {statusLabel(game, me.id)}
      </span>
    </button>
  );
}
