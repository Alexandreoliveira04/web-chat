# Módulo `game`

**Pacote:** `br.edu.webchat.game`
**Situação:** em implementação — jogo da velha pronto; damas, xadrez e jogos solo nas próximas fases

Partidas entre dois colaboradores. **O servidor é a autoridade**: o navegador só envia a
jogada, e o backend confere quem joga, de quem é a vez e se a jogada vale, detecta o fim da
partida, salva e avisa os dois jogadores em tempo real.

## Estrutura

```text
game/
├── controller/   GameController
├── dto/          CreateGameRequest, GameResponse, PlayerResponse
├── engine/       GameEngine, GameOutcome
│   └── tictactoe/  TicTacToeEngine, TicTacToeState
├── entity/       Game, GameType, GameStatus, GameResult
├── repository/   GameRepository
├── service/      GameService, GameChangedEvent
└── websocket/    GameEventBroadcaster
```

Tabela `games`, criada em `V13__create_games.sql`.

## Motores de jogo

Cada jogo é uma classe que implementa `GameEngine<S>` e é registrada como bean; o
`GameService` recebe todas e as indexa pelo `GameType`. Um jogo novo é uma classe nova e um
valor novo no enum, sem mexer no service, no controller nem no banco.

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

Os jogadores são identificados pelo índice: **0 é quem desafiou** e **1 é o adversário**. O
estado `S` é um `record` imutável, gravado em JSON na coluna `state` e enviado ao frontend
como está — por isso não deve guardar nada que os jogadores não possam ver.

| Jogo | `type` | Jogada | Estado |
| ---- | ------ | ------ | ------ |
| Jogo da velha | `TIC_TAC_TOE` | `{ "cell": 0..8 }` (linha por linha) | `{ "board": "X...O....", "toMove": 0 }` — quem desafiou é o `X` e começa |

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
| `RESIGNATION` | um jogador desistiu | o outro |
| `DECLINED` | o desafiado recusou | nulo |
| `CANCELED` | quem desafiou cancelou antes da resposta | nulo |

## Endpoints

Todos exigem autenticação.

| Método | Rota | Quem pode | Resposta |
| ------ | ---- | --------- | -------- |
| `GET` | `/api/v1/games` | — | partidas do usuário: todas as em aberto e as 20 últimas encerradas |
| `POST` | `/api/v1/games` | — | `201` com `Location`; corpo `{ "type", "opponentId", "chatId"? }` |
| `GET` | `/api/v1/games/{id}` | os dois jogadores | `GameResponse` |
| `POST` | `/api/v1/games/{id}/accept` | o desafiado | `GameResponse` |
| `POST` | `/api/v1/games/{id}/decline` | o desafiado | `GameResponse` |
| `POST` | `/api/v1/games/{id}/cancel` | quem desafiou | `GameResponse` |
| `POST` | `/api/v1/games/{id}/resign` | os dois jogadores | `GameResponse` |
| `POST` | `/api/v1/games/{id}/moves` | quem tem a vez | `GameResponse`; corpo é a jogada no formato do jogo |

```json
{
  "id": 7, "type": "TIC_TAC_TOE", "status": "IN_PROGRESS", "result": null, "chatId": 3,
  "challenger": { "id": 1, "name": "Ana", "avatarUrl": null },
  "opponent":   { "id": 2, "name": "Bia", "avatarUrl": null },
  "turnPlayerId": 2, "winnerId": null,
  "state": { "board": "X........", "toMove": 1 },
  "createdAt": "...", "updatedAt": "...", "finishedAt": null
}
```

## WebSocket

| Frame | Destino | Payload | Quem recebe |
| ----- | ------- | ------- | ----------- |
| `SUBSCRIBE` | `/user/queue/games` | `GameResponse` | os dois jogadores, em todas as abas, a cada mudança da partida |

## Regras

- Não se desafia a si mesmo (`400`), nem alguém que não existe (`404`).
- Uma partida **em aberto por jogo e por dupla**: um segundo desafio de jogo da velha entre as
  mesmas duas pessoas, em qualquer direção, dá `409` até o primeiro terminar.
- Desafio feito de uma conversa (`chatId`): quem desafia precisa participar dela (`403`) e o
  adversário também (`400`). Apagar a conversa não apaga a partida (`ON DELETE SET NULL`).
- Só os dois jogadores veem ou mexem na partida (`403`).
- Jogada fora da vez, em casa ocupada, mal formada ou com a partida fora de `IN_PROGRESS`: `400`.
- Duas jogadas ao mesmo tempo na mesma partida: a segunda recebe `409` (`@Version`).
- O adversário não precisa estar online: o desafio e a partida ficam salvos.

## Decisões de implementação

**Comandos por REST, notificações por STOMP.** Diferente das mensagens, aqui a resposta direta
importa: quem joga em casa ocupada recebe o `400` na própria requisição, sem depender da fila
de erros. O resultado de cada comando chega aos dois jogadores por `/user/queue/games`, depois
do commit (`@TransactionalEventListener`), então uma jogada que falhar nunca é anunciada.

**Estado em `TEXT`, não `JSONB`.** O roadmap previa `JSONB`, mas o estado só é lido e gravado
inteiro, nunca consultado por dentro. `TEXT` funciona igual no PostgreSQL e no H2 dos testes,
sem mapeamento especial no Hibernate.

**`flush` antes de responder.** A resposta e o evento levam o `updatedAt` já atualizado; o
frontend usa esse campo para descartar um evento que chegue depois de uma resposta mais nova.

## Testes

| Classe | Tipo | Cobre |
| ------ | ---- | ----- |
| `TicTacToeEngineTest` | unitário | estado inicial, vez, casa ocupada, jogada mal formada, vitória em linha/coluna/diagonal, empate, imutabilidade, ida e volta pelo JSON |
| `GameIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` + cliente STOMP real | partida completa com eventos aos dois; vez e casa ocupada; desistência; quem aceita, recusa e cancela; terceiro não vê nem joga; desafio duplicado; desafio pela conversa; validação; listagem; autenticação |

## Limitações conhecidas

- **Sem tempo limite.** Um desafio sem resposta ou uma partida abandonada ficam em aberto até
  alguém cancelar ou desistir.
- **Sem espectadores.** Em grupo, só os dois jogadores veem a partida.
