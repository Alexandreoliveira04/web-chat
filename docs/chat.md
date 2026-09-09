# Módulo `chat`

**Pacote:** `br.edu.webchat.chat`
**Fases:** 4 (conversas), 5 (mensagens), 6 (WebSocket)
**Situação:** não iniciado — o pacote existe, sem classes

Responsável pelas conversas, participantes, mensagens, histórico e comunicação em
tempo real.

> Este documento descreve o contrato planejado a partir da
> [WEB-CHAT-SPEC.md](../WEB-CHAT-SPEC.md) e deve ser atualizado junto com o código.

## Estrutura prevista

```text
chat/
├── controller/   ChatController, MessageController
├── dto/          ChatRequest, ChatResponse, MessageRequest, MessageResponse
├── entity/       ChatEntity, ChatParticipantEntity, MessageEntity
├── repository/   ChatRepository, MessageRepository
├── service/      ChatService, MessageService
└── websocket/    configuração e handlers STOMP
```

## Escopo

O MVP tem **apenas conversas individuais** — dois participantes. Grupos, envio de
arquivos, edição e exclusão de mensagens, respostas e reações estão fora do escopo.

## Modelo de dados

### `chats`

| Coluna | Tipo | Observações |
| ------ | ---- | ----------- |
| `id` | BIGINT | PK, identity |
| `created_at` | timestamptz | |
| `updated_at` | timestamptz | |

### `chat_participants`

| Coluna | Tipo | Observações |
| ------ | ---- | ----------- |
| `chat_id` | BIGINT | FK → `chats.id` |
| `user_id` | BIGINT | FK → `users.id` |

Restrições:

- um chat individual tem **exatamente dois** participantes;
- um usuário não pode participar duas vezes da mesma conversa — garantido por chave
  única em `(chat_id, user_id)`.

### `messages`

| Coluna | Tipo | Observações |
| ------ | ---- | ----------- |
| `id` | BIGINT | PK, identity |
| `chat_id` | BIGINT | FK → `chats.id` |
| `sender_id` | BIGINT | FK → `users.id` |
| `content` | texto | não pode ser vazio |
| `created_at` | timestamptz | preenchido automaticamente |
| `read_at` | timestamptz | nulo enquanto não lida |

Migrations: `V3__create_chats.sql`, `V4__create_chat_participants.sql`,
`V5__create_messages.sql` (a última migration aplicada é a `V2__create_users.sql`).

## Relacionamentos

```text
User 1:N ChatParticipant N:1 Chat
Chat 1:N Message
User 1:N Message   (remetente)
```

## API REST

| Método | Rota | Descrição |
| ------ | ---- | --------- |
| `GET` | `/api/v1/chats` | Lista as conversas do usuário autenticado |
| `POST` | `/api/v1/chats` | Inicia uma conversa individual |
| `GET` | `/api/v1/chats/{chatId}` | Consulta a conversa e seus participantes |
| `GET` | `/api/v1/chats/{chatId}/messages` | Histórico de mensagens |
| `POST` | `/api/v1/chats/{chatId}/messages` | Envia uma mensagem |

Todos exigem autenticação.

## Divisão REST × WebSocket

| REST | WebSocket |
| ---- | --------- |
| Criação e listagem de conversas | Envio de mensagens em tempo real |
| Consulta de histórico | Recebimento de mensagens |
| Operações persistentes | Eventos de chat |

A persistência é sempre responsabilidade do service — tanto o caminho REST quanto o
WebSocket passam por ele, para que não existam dois caminhos de escrita divergentes.

## Regras de negócio

- criar uma conversa exige exatamente dois participantes distintos;
- se já existir conversa entre os dois usuários, ela é reaproveitada em vez de
  duplicada (comportamento a confirmar na fase 4: reaproveitar ou retornar 409);
- só participantes da conversa podem lê-la ou enviar mensagens nela — caso contrário
  **403**;
- `chatId` inexistente resulta em `NotFoundException` (404);
- o remetente de uma mensagem é sempre o usuário autenticado, nunca um id vindo do
  corpo da requisição;
- conteúdo vazio ou só com espaços é rejeitado (400).

## WebSocket

Abordagem prevista: **STOMP sobre WebSocket**, por ser a opção integrada ao
ecossistema Spring.

Pontos a definir e documentar durante a fase 6:

- endpoint de conexão;
- destinos (`/topic`, `/queue`, prefixos de aplicação);
- formato das mensagens trafegadas;
- como o JWT autentica o handshake — ver [auth](auth.md).

## Desempenho

Listagens de conversas e histórico devem evitar N+1 (RNF03). O histórico deve ser
paginado; a estratégia de paginação será definida na fase 5.

## Pontos em aberto

- reaproveitar conversa existente ou retornar conflito;
- estratégia de paginação do histórico;
- quem marca a mensagem como lida e em que momento `read_at` é preenchido;
- se a listagem de conversas traz a última mensagem embutida.
