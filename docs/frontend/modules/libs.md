# Lib (`lib/`)

Camada entre as telas e o backend. Nenhum componente usa `fetch` ou STOMP diretamente: tudo
passa por aqui, então o tratamento de token, erro e sessão expirada existe em um lugar só.

| Arquivo | Responsabilidade |
| ------- | ---------------- |
| [`api.ts`](#apits) | cliente REST tipado, token e erros |
| [`realtime.ts`](#realtimets) | conexão STOMP, assinaturas e envio |
| [`types.ts`](#typests) | espelho dos DTOs da API |
| [`format.ts`](#formatts) | título, prévia, iniciais, datas e ordenação |

## `api.ts`

**Base da API.** `API_URL` é `NEXT_PUBLIC_API_URL` ou, na ausência dela, o caminho relativo
`/api/v1` — o que faz o mesmo build funcionar servido pelo próprio backend.

**Token.** `tokenStorage` encapsula o `localStorage` (chave `webchat_token`) e devolve
`null` fora do navegador, para o código não quebrar durante o build estático.

**`request<T>`** é o único ponto que fala com a rede. Ele:

1. injeta `Content-Type` e `Authorization` quando há token;
2. em **401**, limpa o token e redireciona para `/login` (sem redirecionar se já estiver
   lá, para não entrar em laço) antes de lançar o erro;
3. em outro erro, lê o `ApiError` do corpo e lança `RequestError` com `status` e `fields`;
4. devolve `undefined` em `204` e o JSON tipado nos demais casos.

`RequestError.message` junta a mensagem com os valores de `fields`, então a validação do
backend chega pronta para ser exibida — a interface não reescreve regra de negócio.

Os grupos de chamadas espelham os módulos do backend:

| Grupo | Chamadas |
| ----- | -------- |
| `authApi` | `login` (já guarda o token), `register`, `logout` |
| `usersApi` | `me`, `list`, `update` |
| `chatsApi` | `list`, `byId`, `openDirect`, `createGroup`, `rename`, `addParticipant`, `removeParticipant`, `leave` |
| `messagesApi` | `history`, `send`, `edit`, `remove`, `markAsRead` |

## `realtime.ts`

`connectRealtime(handlers)` cria o `Client` do `@stomp/stompjs` e devolve o cliente ativo.

**URL do broker.** Derivada da base REST: com `NEXT_PUBLIC_API_URL` absoluta, troca `http`
por `ws` e `/api/v1` por `/ws`; sem ela, monta a partir de `window.location` (e usa `wss` em
HTTPS).

**Autenticação.** O JWT vai em `connectHeaders` do frame `CONNECT` — a API de WebSocket do
navegador não permite enviar `Authorization` no handshake.

**Assinaturas** (feitas no `onConnect`, uma vez por conexão): `/user/queue/messages`,
`/user/queue/message-updates`, `/user/queue/read`, `/user/queue/chats` e `/topic/presence`.
Cada frame é desserializado antes de chegar ao handler.

**Reconexão** automática a cada 5 s. `onConnect` recebe `reconectado: boolean`, que
distingue a primeira conexão das seguintes — é o que permite à página recarregar conversas e
histórico só quando volta de uma queda.

**Erro de autenticação.** Se o frame `ERROR` mencionar autenticação, o cliente é desativado,
o token é limpo e o usuário volta para `/login`; os outros erros vão para `onError` e viram
toast.

`sendOverWebSocket(client, chatId, content)` publica em `/app/chats/{chatId}/messages` e
devolve `false` se não houver conexão — é esse `false` que faz a página cair para o `POST`
REST.

## `types.ts`

Interfaces que espelham os DTOs do backend: `User`, `Participant`, `Message`, `Chat`,
`MessageHistory`, `ReadReceipt`, `PresenceEvent`, `ChatEvent` e `ApiError`, mais os literais
`UserStatus`, `Role` e `ChatType`.

Escrito à mão, sem geração a partir de OpenAPI: o contrato é pequeno e estável, e o custo de
manter um gerador no build não se paga aqui. A contrapartida é que uma mudança no DTO do
backend precisa ser refletida neste arquivo — o TypeScript acusa o uso errado, não a
divergência.

## `format.ts`

Funções puras, sem estado nem acesso à rede:

| Função | Devolve |
| ------ | ------- |
| `otherParticipant` | o outro participante de uma conversa individual |
| `chatTitle` | nome do grupo, ou nome do outro participante |
| `chatSubtitle` | "N participantes" em grupo; "online"/"offline" em conversa individual |
| `isOnline` | se o outro participante está online (sempre `false` em grupo) |
| `messagePreview` | prévia da última mensagem, com prefixo "Você: " ou o primeiro nome em grupo, e "mensagem apagada" quando for o caso |
| `initials` | iniciais do nome, para o avatar |
| `formatTime` | hora (hoje) ou data + hora, em `pt-BR` |
| `sortChats` | conversas por `updatedAt` decrescente, desempatando por id |
| `contactsWithoutMe` | colaboradores exceto o próprio usuário, em ordem alfabética |

Ficam separadas porque são as regras de apresentação reaproveitadas por barra lateral,
cabeçalho e modais — e, sendo puras, mudam de comportamento sem tocar em componente.
