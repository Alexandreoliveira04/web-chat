# Lib (`lib/`)

Camada entre as telas e o backend. Nenhum componente usa `fetch` ou STOMP diretamente: tudo
passa por aqui, então o tratamento de token, erro e sessão expirada existe em um lugar só.

| Arquivo | Responsabilidade |
| ------- | ---------------- |
| [`api.ts`](#apits) | cliente REST tipado, token e erros |
| [`realtime.ts`](#realtimets) | conexão STOMP, assinaturas e envio |
| [`useVoiceCall.ts`](#usevoicecallts) | chamada de voz: estado, microfone, toque e WebRTC |
| [`ringtone.ts`](#ringtonets) | toque da chamada, gerado com Web Audio |
| [`attachments.ts`](#attachmentsts) | reconhece fotos, vídeos e documentos nas mensagens |
| [`types.ts`](#typests) | espelho dos DTOs da API |
| [`format.ts`](#formatts) | título, foto, prévia, iniciais, datas e ordenação |
| `cropImage.ts` | recorte da foto de perfil num `<canvas>` antes do envio |

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
| `usersApi` | `me`, `list`, `update`, `uploadAvatar` |
| `chatsApi` | `list` (com busca opcional), `byId`, `openDirect`, `createGroup`, `rename`, `uploadAvatar`, `addParticipant`, `removeParticipant`, `leave` |
| `messagesApi` | `history`, `shared` (arquivos e links da conversa), `send`, `edit`, `remove`, `markAsRead` |
| `callsApi` | `iceServers` (servidores STUN/TURN das chamadas) |
| `filesApi` | `upload` (envia para o Cloud Storage e devolve a URL) |

`getAssetUrl` completa um caminho relativo com a base do backend; URLs absolutas, como as do
Cloud Storage, passam direto.

## `realtime.ts`

`connectRealtime(handlers)` cria o `Client` do `@stomp/stompjs` e devolve o cliente ativo.

**URL do broker.** Derivada da base REST: com `NEXT_PUBLIC_API_URL` absoluta, troca `http`
por `ws` e `/api/v1` por `/ws`; sem ela, monta a partir de `window.location` (e usa `wss` em
HTTPS).

**Autenticação.** O JWT vai em `connectHeaders` do frame `CONNECT` — a API de WebSocket do
navegador não permite enviar `Authorization` no handshake.

**Assinaturas** (feitas no `onConnect`, uma vez por conexão): `/user/queue/messages`,
`/user/queue/message-updates`, `/user/queue/read`, `/user/queue/chats`, `/topic/presence`,
`/topic/profiles`, `/topic/users`, `/user/queue/calls`, `/user/queue/call-signal` e `/user/queue/errors`.
Cada frame é desserializado antes de chegar ao handler; os erros chegam a `onError` só com a
mensagem.

**Reconexão** automática a cada 5 s. `onConnect` recebe `reconectado: boolean`, que
distingue a primeira conexão das seguintes — é o que permite à página recarregar conversas e
histórico só quando volta de uma queda.

**Erro de autenticação.** Se o frame `ERROR` mencionar autenticação, o cliente é desativado,
o token é limpo e o usuário volta para `/login`; os outros erros vão para `onError` e viram
toast.

`sendOverWebSocket(client, chatId, content)` publica em `/app/chats/{chatId}/messages` e
devolve `false` se não houver conexão — é esse `false` que faz a página cair para o `POST`
REST.

`publishCallAction(client, chatId, "start" | "accept" | "end")` e
`publishCallSignal(client, chatId, type, data)` publicam nos destinos das chamadas, com o
mesmo retorno `false` quando não há conexão.

## `useVoiceCall.ts`

Hook que concentra a chamada de voz; a página `/chat` o usa uma vez e repassa o estado aos
componentes. Devolve `state` (`status`, `chatId`, `peerId`, `muted`, `startedAt`) e as ações
`start`, `accept`, `hangUp` e `toggleMute`, mais `handleEvent` e `handleSignal`, que a página
liga aos handlers do STOMP, e `reset`, chamado quando a conexão volta de uma queda.

- **Microfone**: `getUserMedia` com cancelamento de eco, supressão de ruído e ganho
  automático. Sem permissão ou fora de contexto seguro, avisa e não liga.
- **WebRTC**: um `RTCPeerConnection` com os servidores STUN/TURN que vêm do backend
  (`callsApi.iceServers`, buscados uma vez por sessão; se falhar, um STUN público de reserva). Quem ligou cria a oferta
  depois do `ACCEPTED`; quem atendeu responde. Candidatos ICE que chegam antes da descrição
  remota esperam numa fila.
- **Áudio remoto**: tocado num `Audio` criado pelo hook, fora da árvore do React.
- **Tempo limite**: quem liga desiste depois de 30 s e avisa "não atendeu".
- **Outras abas**: o toque vai para todas as abas de quem recebe; as que não atenderam
  encerram ao ver o `ACCEPTED`.
- **Avisos**: os motivos de encerramento viram mensagens como "Bia recusou a chamada" ou "A
  chamada caiu", entregues pelo callback `onNotice`.

Os handlers do STOMP são registrados uma vez, então o hook guarda estado e callbacks em refs
para lê-los sempre atualizados, no mesmo padrão da página.

## `ringtone.ts`

`playRingtone(frequencia, volume)` gera dois bipes a cada 2 segundos com um oscilador do Web
Audio, sem arquivo de som, e devolve a função que para o toque. O hook usa 660 Hz para
quem recebe e 400 Hz, mais baixo, para o "chamando" de quem liga. Para trocar por um áudio
próprio, basta uma função com o mesmo contrato (tocar em loop e devolver a função de parar)
usando um `Audio` com o arquivo em `public/`.

## `attachments.ts`

Um arquivo enviado vira uma mensagem cujo conteúdo é só a URL. `attachmentOf(content)`
reconhece essas mensagens e devolve `{ kind, url, name, extension }`, ou `null` para texto
comum:

| `kind` | Quando |
| ------ | ------ |
| `image` | extensão de imagem (`jpg`, `png`, `webp`, `gif`...) |
| `video` | extensão de vídeo (`mp4`, `webm`, `mov`...) |
| `document` | extensão de documento (`pdf`, `docx`, `xlsx`, `csv`, `zip`...) ou qualquer outro arquivo do Cloud Storage |

O `name` é o nome original, tirado da URL: o objeto fica em `.../o/<uuid>%2F<nome>`, então
o nome é o último trecho depois de decodificar.

`linksIn(text)` lista as URLs de um texto, para a aba Links. `MessageItem` e
`ChatDetailsSidebar` usam as mesmas funções, então uma mensagem nunca é foto num lugar e link
no outro.

## `types.ts`

Interfaces que espelham os DTOs do backend: `User`, `Participant`, `Message`, `Chat`,
`MessageHistory`, `ReadReceipt`, `PresenceEvent`, `ProfileEvent`, `ChatEvent`, `CallEvent`,
`CallSignal` e `ApiError`, mais os literais `UserStatus`, `Role`, `ChatType`,
`CallEndReason` e `SignalType`.

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
| `chatAvatarUrl` | foto do grupo, ou do outro participante em conversa individual |
| `chatSubtitle` | "N participantes" em grupo; "online"/"offline" em conversa individual |
| `isOnline` | se o outro participante está online (sempre `false` em grupo) |
| `messagePreview` | prévia da última mensagem, com prefixo "Você: " ou o primeiro nome em grupo, e "mensagem apagada" quando for o caso |
| `initials` | iniciais do nome, para o avatar |
| `formatTime` | hora (hoje) ou data + hora, em `pt-BR` |
| `sortChats` | conversas por `updatedAt` decrescente, desempatando por id |
| `contactsWithoutMe` | colaboradores exceto o próprio usuário, em ordem alfabética |

Ficam separadas porque são as regras de apresentação reaproveitadas por barra lateral,
cabeçalho e modais — e, sendo puras, mudam de comportamento sem tocar em componente.
