# Módulo `game`

**Pacote:** `br.edu.webchat.game`
**Situação:** implementado — jogo da velha, damas e xadrez entre dois colaboradores; campo
minado e paciência para jogar sozinho, com ranking

Duas partes:

- **Partidas entre dois colaboradores.** O servidor é a autoridade: o navegador só envia a
  jogada, e o backend confere quem joga, de quem é a vez e se a jogada vale, detecta o fim da
  partida, salva e avisa os dois jogadores em tempo real.
- **Jogos solo.** Rodam inteiros no navegador; o backend só guarda o tempo das vitórias e
  monta o ranking.

## Estrutura

```text
game/
├── config/       GameClockConfig (Clock e agendamento)
├── controller/   GameController, SoloGameController
├── dto/          CreateGameRequest, GameResponse, ClockResponse, PlayerResponse,
│                 SoloResultRequest, SoloResultResponse, SoloRankingResponse
├── engine/       GameEngine, GameOutcome
│   ├── tictactoe/  TicTacToeEngine, TicTacToeState
│   ├── checkers/   CheckersEngine, CheckersState
│   └── chess/      ChessEngine, ChessState, ChessEnding
├── entity/       Game, GameType, GameStatus, GameResult, SoloGame, SoloResult
├── repository/   GameRepository, SoloResultRepository
├── service/      GameService, GameChangedEvent, GameClockScheduler, SoloRankingService
└── websocket/    GameEventBroadcaster
```

Tabelas `games` (`V13__create_games.sql`, mais relógio e proposta de empate em
`V15__add_clock_and_draw_offer_to_games.sql`) e `solo_results` (`V14__create_solo_results.sql`).

## Motores de jogo

Cada jogo de duas pessoas é uma classe que implementa `GameEngine<S>` e é registrada como
bean; o `GameService` recebe todas e as indexa pelo `GameType`. Um jogo novo é uma classe nova
e um valor novo no enum, sem mexer no service, no controller nem no banco.

```java
public interface GameEngine<S> {
    GameType type();
    Class<S> stateType();
    S initialState();
    int playerToMove(S state);                        // 0 ou 1
    S applyMove(S state, int player, JsonNode move);  // BadRequestException se inválida
    GameOutcome outcome(S state);                     // em andamento, vitória de 0/1 ou empate
}
```

Os jogadores são identificados pelo índice: **0 é quem desafiou** (o `X`, as brancas) e
começa; **1 é o adversário**. O estado `S` é um `record` imutável, gravado em JSON na coluna
`state` e enviado ao frontend como está — por isso não deve guardar nada que os jogadores não
possam ver. Damas e xadrez incluem no estado as **jogadas legais** de quem joga, já calculadas:
o tabuleiro do frontend só oferece essas, sem repetir as regras.

| Jogo | `type` | Jogada | Estado |
| ---- | ------ | ------ | ------ |
| Jogo da velha | `TIC_TAC_TOE` | `{ "cell": 0..8 }` (linha por linha) | `{ "board": "X...O....", "toMove": 0 }` |
| Damas | `CHECKERS` | `{ "path": [origem, parada1, parada2, ...] }`, casas 0..63 | `board` (64 caracteres: `w`/`W` brancas, `b`/`B` pretas, `.`), `toMove`, `quietMoves`, `moves` (caminhos legais), `lastMove`, `lastCaptured` |
| Xadrez | `CHESS` | `{ "move": "e2e4" }` em UCI; promoção com a peça no fim (`e7e8q`) | `fen`, `toMove`, `history` (UCI), `san` (`e4`, `Qxf7#`), `moves` (UCI legais), `check`, `ending` |

### Damas: regras brasileiras

- Pedra anda uma casa na diagonal para a frente, mas **captura para a frente e para trás**.
- A **captura é obrigatória**, e vale a **lei da maioria**: entre as capturas possíveis, só as
  que levam o maior número de peças.
- Numa captura múltipla, as peças capturadas só saem no fim, não podem ser puladas duas vezes
  e continuam bloqueando o caminho.
- A **dama voa**: anda e captura a qualquer distância, e pode pousar em qualquer casa livre
  depois da peça capturada.
- Pedra que **termina** a jogada na última linha vira dama; passar por ela no meio de uma
  captura não conta.
- Perde quem não tem jogada (sem peças ou bloqueado).
- **Empate** depois de 40 jogadas seguidas (20 de cada lado) só com damas e sem captura.

Casas numeradas de 0 a 63, linha por linha a partir do canto superior esquerdo; só as escuras
(`(linha + coluna)` ímpar) são usadas. As brancas começam embaixo (linhas 5 a 7).

### Xadrez: chesslib

As regras vêm da biblioteca [chesslib](https://github.com/bhlangonijr/chesslib) (Apache-2.0),
publicada só no JitPack, que o `pom.xml` declara em `<repositories>`: roque, en passant,
promoção, xeque-mate e os empates — afogamento, repetição tripla, material insuficiente e 50
lances. O motivo do fim fica em `state.ending` (`CHECKMATE`, `STALEMATE`, `REPETITION`,
`INSUFFICIENT_MATERIAL`, `FIFTY_MOVES`).

A fonte da verdade é `history`: a cada jogada o tabuleiro é refeito desde a posição inicial.
O FEN sozinho perderia as posições anteriores, e com elas a repetição tripla.

## Relógio e proposta de empate

Só no xadrez e nas damas (`GameType.isTimed()`); o jogo da velha não tem nenhum dos dois.

**Ritmo.** Quem desafia escolhe 3, 5 ou 10 minutos para cada jogador
(`timeControlMinutes`). Sem incremento. O relógio de quem começa liga quando o desafio é
aceito.

**O servidor é o dono do relógio.** A entidade guarda o tempo de cada um no início da vez e
quando a vez começou (`turn_started_at`). A cada jogada, o tempo gasto é descontado de quem
jogou; se ele já tinha acabado, a jogada **não vale** e a partida termina por tempo. Para quem
simplesmente para de jogar, o `GameClockScheduler` roda a cada segundo
(`app.games.clock-check-ms`) e encerra as partidas cujo prazo (`turn_deadline_at`, indexado)
passou. Desistir e empatar também param o relógio, descontando a vez em andamento.

A resposta traz o tempo de cada lado **no momento em que foi montada** e de quem é o tempo
que está correndo (`clock.runningPlayerId`); o navegador desconta desse lado o que passou desde
que recebeu a resposta. Assim a diferença de relógio entre o computador e o servidor não
importa, só a latência.

**Empate.** Um jogador propõe (`POST .../draw`); o outro aceita (`.../draw/accept`) ou recusa
(`.../draw/decline`). Fazer uma jogada também recusa a proposta pendente. Se os dois propõem,
a segunda proposta já empata.

## Ciclo de uma partida

```text
           aceitar                 fim pelas regras / desistência
INVITED ────────────► IN_PROGRESS ────────────────────────────────► FINISHED
   │                                                                    ▲
   └──────────── recusar (DECLINED) / cancelar (CANCELED) ──────────────┘
```

| `result` | Quando | `winnerId` |
| -------- | ------ | ---------- |
| `WIN` | alguém venceu pelas regras | o vencedor |
| `DRAW` | empate pelas regras | nulo |
| `AGREED_DRAW` | os dois aceitaram empatar | nulo |
| `RESIGNATION` | um jogador desistiu | o outro |
| `TIMEOUT` | o tempo de um jogador acabou | o outro |
| `DECLINED` | o desafiado recusou | nulo |
| `CANCELED` | quem desafiou cancelou antes da resposta | nulo |

## Endpoints

Todos exigem autenticação.

### Partidas

| Método | Rota | Quem pode | Resposta |
| ------ | ---- | --------- | -------- |
| `GET` | `/api/v1/games` | — | partidas do usuário: todas as em aberto e as 20 últimas encerradas |
| `POST` | `/api/v1/games` | — | `201` com `Location`; corpo `{ "type", "opponentId", "chatId"?, "timeControlMinutes"? }` (ritmo obrigatório no xadrez e nas damas) |
| `GET` | `/api/v1/games/{id}` | os dois jogadores | `GameResponse` |
| `POST` | `/api/v1/games/{id}/accept` | o desafiado | `GameResponse` |
| `POST` | `/api/v1/games/{id}/decline` | o desafiado | `GameResponse` |
| `POST` | `/api/v1/games/{id}/cancel` | quem desafiou | `GameResponse` |
| `POST` | `/api/v1/games/{id}/resign` | os dois jogadores | `GameResponse` |
| `POST` | `/api/v1/games/{id}/draw` | os dois jogadores | `GameResponse`; propõe empate (ou aceita, se o outro já propôs) |
| `POST` | `/api/v1/games/{id}/draw/accept` | quem recebeu a proposta | `GameResponse` |
| `POST` | `/api/v1/games/{id}/draw/decline` | quem recebeu a proposta | `GameResponse` |
| `POST` | `/api/v1/games/{id}/moves` | quem tem a vez | `GameResponse`; corpo é a jogada no formato do jogo |

```json
{
  "id": 7, "type": "TIC_TAC_TOE", "status": "IN_PROGRESS", "result": null, "chatId": 3,
  "challenger": { "id": 1, "name": "Ana", "avatarUrl": null },
  "opponent":   { "id": 2, "name": "Bia", "avatarUrl": null },
  "turnPlayerId": 2, "winnerId": null,
  "state": { "board": "X........", "toMove": 1 },
  "clock": null, "drawOfferedBy": null,
  "createdAt": "...", "updatedAt": "...", "finishedAt": null
}

// xadrez de 5 minutos, vez das pretas
"clock": { "initialMs": 300000, "challengerMs": 287000, "opponentMs": 294500, "runningPlayerId": 2 }
```

### Jogos solo

| Método | Rota | Resposta |
| ------ | ---- | -------- |
| `GET` | `/api/v1/games/solo/{game}/ranking?mode=EASY` | `SoloRankingResponse` |
| `POST` | `/api/v1/games/solo/{game}/results` | `201` com `SoloResultResponse`; corpo `{ "mode", "durationMs", "moves"? }` |

| `game` | `mode` |
| ------ | ------ |
| `MINESWEEPER` | `EASY` (9×9, 10 minas), `MEDIUM` (16×16, 40), `HARD` (16×30, 99) |
| `SOLITAIRE` | `DRAW_ONE`, `DRAW_THREE` (cartas viradas do monte por vez) |

```json
// POST /api/v1/games/solo/MINESWEEPER/results  { "mode": "EASY", "durationMs": 42000 }
{
  "durationMs": 42000,
  "personalBest": true,
  "ranking": {
    "game": "MINESWEEPER", "mode": "EASY",
    "top": [ { "position": 1, "player": { "id": 1, "name": "Ana", "avatarUrl": null },
               "bestDurationMs": 42000, "wins": 3 } ],
    "me":    { "position": 1, "...": "..." }
  }
}
```

O ranking traz o **melhor tempo de cada pessoa** (uma linha por pessoa), as 10 primeiras em
`top` e a posição de quem consultou em `me`, mesmo fora do top (`null` se ainda não venceu).

## WebSocket

| Frame | Destino | Payload | Quem recebe |
| ----- | ------- | ------- | ----------- |
| `SUBSCRIBE` | `/user/queue/games` | `GameResponse` | os dois jogadores, em todas as abas, a cada mudança da partida |

O ranking solo não tem tempo real: é carregado ao abrir o jogo e volta atualizado na resposta
de cada vitória.

## Regras

- Não se desafia a si mesmo (`400`), nem alguém que não existe (`404`).
- Uma partida **em aberto por jogo e por dupla**: um segundo desafio do mesmo jogo entre as
  mesmas duas pessoas, em qualquer direção, dá `409` até o primeiro terminar. Jogos diferentes
  entre a mesma dupla podem correr juntos.
- Desafio feito de uma conversa (`chatId`): quem desafia precisa participar dela (`403`) e o
  adversário também (`400`). Apagar a conversa não apaga a partida (`ON DELETE SET NULL`).
- Só os dois jogadores veem ou mexem na partida (`403`).
- Jogada fora da vez, ilegal, mal formada ou com a partida fora de `IN_PROGRESS`: `400`.
- Duas jogadas ao mesmo tempo na mesma partida: a segunda recebe `409` (`@Version`).
- O adversário não precisa estar online para receber o desafio. No xadrez e nas damas,
  porém, o relógio corre a partir do aceite: quem não jogar perde por tempo.
- Ritmo diferente de 3, 5 ou 10 minutos, ou ausente no xadrez e nas damas: `400`; ritmo no
  jogo da velha: `400`.
- Proposta de empate só no xadrez e nas damas; aceitar ou recusar sem proposta do adversário: `400`.
- Resultado solo: modo precisa existir para o jogo (`400`; aceita minúsculas), tempo entre 1
  segundo e 24 horas, jogadas não negativas.

## Decisões de implementação

**Comandos por REST, notificações por STOMP.** Diferente das mensagens, aqui a resposta direta
importa: quem joga em casa ocupada recebe o `400` na própria requisição, sem depender da fila
de erros. O resultado de cada comando chega aos dois jogadores por `/user/queue/games`, depois
do commit (`@TransactionalEventListener`), então uma jogada que falhar nunca é anunciada.

**Estado em `TEXT`, não `JSONB`.** O estado só é lido e gravado inteiro, nunca consultado por
dentro. `TEXT` funciona igual no PostgreSQL e no H2 dos testes, sem mapeamento especial.

**`flush` antes de responder.** A resposta e o evento levam o `updatedAt` já atualizado; o
frontend usa esse campo para descartar um evento que chegue depois de uma resposta mais nova.

**Jogadas legais no estado.** Damas e xadrez mandam a lista pronta. Assim as regras existem num
lugar só (o servidor), e o tabuleiro do navegador só destaca e envia o que vale.

**Xadrez conferido contra `legalMoves()`.** O `doMove` do chesslib, mesmo com validação ligada,
aceita jogadas impossíveis (`e2e5` na posição inicial). O motor só aplica uma jogada que esteja
na lista de legais.

**Damas com motor próprio.** As regras brasileiras (lei da maioria, dama que voa, pedra que
captura para trás) não estão nas bibliotecas Java comuns, que seguem as regras americanas ou as
internacionais em 10×10.

**Jogos solo no navegador, com confiança no tempo.** Validar uma partida de campo minado ou
paciência exigiria jogá-la no servidor, jogada por jogada. Para um ranking entre colegas não
compensa: o servidor aceita o tempo enviado dentro de limites plausíveis.

## Testes

| Classe | Tipo | Cobre |
| ------ | ---- | ----- |
| `TicTacToeEngineTest` | unitário | estado inicial, vez, casa ocupada, jogada mal formada, vitória em linha/coluna/diagonal, empate, imutabilidade, ida e volta pelo JSON |
| `CheckersEngineTest` | unitário | posição inicial e jogadas de abertura; captura obrigatória; pedra captura para trás; lei da maioria; promoção só ao terminar na última linha; dama voa e não pula duas peças; sem jogada perde; empate por jogadas só de damas; JSON |
| `ChessEngineTest` | unitário | posição inicial; histórico, SAN e vez; jogada ilegal que o `doMove` aceitaria; mate do louco; promoção com a peça; repetição tripla; JSON |
| `GameClockTest` | unitário | só o tempo de quem joga corre; fim da vez desconta e passa o relógio; tempo esgotado; terminar para o relógio e limpa a proposta de empate |
| `GameIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` + cliente STOMP real | partida completa com eventos aos dois; vez e casa ocupada; desistência; quem aceita, recusa e cancela; terceiro não vê nem joga; desafio duplicado; desafio pela conversa; validação; listagem; xadrez e damas pela API; jogos diferentes juntos; ritmo obrigatório; relógio no aceite e a cada jogada; tempo esgotado; empate aceito, recusado, cruzado e anulado por jogada; autenticação |
| `SoloRankingIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` | recorde pessoal; melhor tempo por pessoa e vitórias; ranking separado por jogo e modo; quem não venceu; validação de jogo, modo e tempo; autenticação |

A lógica do campo minado e da paciência fica no frontend (`lib/minesweeper.ts` e
`lib/solitaire.ts`), em funções puras.

## Limitações conhecidas

- **Sem tempo limite.** Um desafio sem resposta ou uma partida abandonada ficam em aberto até
  alguém cancelar ou desistir.
- **Tempo esgotado sempre perde**, mesmo quando o adversário não teria peças para dar mate
  (pela regra oficial do xadrez, seria empate).
- **Sem incremento** nem ritmos personalizados: só 3, 5 e 10 minutos.
- **Damas:** as regras especiais de final da CBD (por exemplo, 3 damas contra 1) não estão
  implementadas; vale só o limite de jogadas seguidas com damas.
- **Sem espectadores.** Em grupo, só os dois jogadores veem a partida.
- **Ranking solo depende do navegador:** quem alterar o cliente consegue enviar um tempo falso,
  dentro dos limites.
- **Dependência do JitPack** para o chesslib: se o JitPack estiver fora do ar num build sem
  cache, o Maven não baixa a biblioteca.
