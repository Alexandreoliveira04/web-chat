# Arquitetura — Frontend

**Diretório:** `frontend/`

Interface web do Web Chat: login, cadastro, conversas individuais e em grupo, envio, edição
e exclusão de mensagens, não lidas, ✓✓ e presença — tudo em tempo real.

A documentação de cada parte fica em [modules/](modules/):
[páginas](modules/pages.md), [componentes](modules/components.md) e
[lib](modules/libs.md).

## Stack

| Item | Escolha |
| ---- | ------- |
| Framework | Next.js 16 (App Router) |
| Linguagem | TypeScript |
| UI | React 19 |
| Estilos | Tailwind CSS 4 |
| Tempo real | `@stomp/stompjs` 7 |
| Emojis | `emoji-picker-element` (carregado sob demanda) |
| Build | `output: "export"` — HTML/JS estáticos, sem servidor Node em produção |

> **Decisão: export estático.** A aplicação é inteiramente client-side — o token fica no
> navegador e toda chamada é REST ou WebSocket autenticada com ele —, então não há o que
> renderizar no servidor. Com o export, o Spring Boot continua sendo o único processo a
> subir e o deploy segue sendo um único artefato.

Como consequência, toda página é `"use client"` e não existe rota de API no Next: o
backend é a única origem de dados.

## Estrutura

```text
frontend/
├── app/
│   ├── layout.tsx          tema escuro e metadados
│   ├── page.tsx            redireciona para /login ou /chat conforme o token
│   ├── login/page.tsx      login e cadastro no mesmo formulário
│   └── chat/page.tsx       tela principal: estado, efeitos e handlers de tempo real
├── components/             Sidebar, ChatWindow, MessageItem, modais, Avatar
├── lib/
│   ├── api.ts              cliente REST tipado e armazenamento do token
│   ├── realtime.ts         conexão STOMP e assinaturas
│   ├── types.ts            espelho dos DTOs da API
│   └── format.ts           título, prévia, iniciais e datas
└── next.config.ts          output: "export"
```

O estado vive todo em `app/chat/page.tsx`; os componentes são de apresentação e recebem
dados e callbacks por props. Não há Redux, Zustand nem Context — uma tela só, com um dono
de estado, não justifica a camada extra. Detalhes em [páginas](modules/pages.md).

## Comunicação com o backend

Dois caminhos, ambos autenticados com o mesmo JWT:

| Caminho | Arquivo | Uso |
| ------- | ------- | --- |
| REST | [`lib/api.ts`](modules/libs.md#apits) | login, cadastro, perfil, conversas, histórico, leitura |
| STOMP | [`lib/realtime.ts`](modules/libs.md#realtimets) | receber mensagens, edições, ✓✓, eventos de grupo e presença; enviar mensagem |

`NEXT_PUBLIC_API_URL` define a base REST. Sem ela, o cliente usa o caminho relativo
`/api/v1` e deriva o WebSocket de `window.location` — por isso o **mesmo build** funciona em
qualquer host, sem reconfiguração.

## Autenticação

O token JWT fica no `localStorage` (chave `webchat_token`) e é enviado em `Authorization`
nas chamadas REST e no frame `CONNECT` do STOMP. Qualquer resposta `401` limpa o token e
manda o usuário para `/login`; o mesmo acontece se o servidor recusar a autenticação do
WebSocket.

> **Limitação conhecida.** Guardar o token no `localStorage` o expõe a um eventual XSS. A
> alternativa (cookie `HttpOnly` + CSRF) exigiria sessão no servidor ou refresh token, que
> estão fora do escopo. O React escapa todo conteúdo renderizado por padrão, e o projeto não
> usa `dangerouslySetInnerHTML` em lugar nenhum.

## Tempo real

`lib/realtime.ts` assina os cinco destinos do backend (ver
[chat](../backend/modules/chat.md#websocket)) e a página `/chat` reage assim:

| Evento | Efeito na interface |
| ------ | ------------------- |
| `/user/queue/messages` | mensagem entra na conversa aberta; nas outras, incrementa o contador e atualiza a prévia; conversa desconhecida (grupo novo) é recarregada |
| `/user/queue/message-updates` | substitui a mensagem editada, ou mostra "mensagem apagada" |
| `/user/queue/read` | atualiza o ✓✓; se quem leu foi o próprio usuário em outra aba, zera o contador |
| `/user/queue/chats` | `CREATED`/`UPDATED` recarregam a conversa; `REMOVED` tira o grupo da lista |
| `/topic/presence` | atualiza o ponto verde no avatar e o "online/offline" no cabeçalho |

**Envio.** Com o WebSocket conectado, a mensagem é publicada em `/app/chats/{id}/messages` e
só aparece quando o servidor a devolve — um único caminho de renderização, igual para todas
as abas. Sem conexão, cai para o `POST` REST. Ids repetidos são ignorados.

**Reconexão** a cada 5 s. Ao reconectar, a lista de conversas e o histórico aberto são
recarregados, para recuperar o que chegou enquanto a conexão esteve fora.

## Leitura

Ao abrir uma conversa com não lidas, o front chama `PATCH .../messages/read`. Se a aba
estiver em segundo plano quando a mensagem chegar, a marcação espera o usuário voltar
(`visibilitychange`). O ✓✓ usa `lastReadByOthersMessageId`, então em grupo só aparece quando
**todos** os outros participantes leram.

## Build

**Desenvolvimento** (front em `:3000`, backend em `:8080`, com recarga automática):

```bash
cd frontend
npm install
npm run dev
```

Em desenvolvimento o front e a API ficam em portas diferentes, então é preciso apontar a
base da API. Crie `frontend/.env.development` (ignorado pelo git):

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

O backend libera essa origem via `CORS_ALLOWED_ORIGINS` e `WS_ALLOWED_ORIGINS`, cujos
padrões já aceitam `http://localhost:*`.

**Build servido pelo backend** (mesma origem, sem CORS):

```bash
cd frontend
npm run build             # gera frontend/out
```

O `pom.xml` declara `frontend/out` como um `<resource>` com `targetPath` `static`, então o
Maven empacota o front junto com o jar — não existe passo de cópia nem diretório gerado
dentro de `src/`. Se `frontend/out` não existir, o Maven apenas avisa e a aplicação sobe só
com a API. No `Dockerfile`, um estágio Node gera `frontend/out` e o estágio Maven o empacota
da mesma forma.

Como o App Router gera caminhos sem extensão, o `StaticResourceConfig` do backend resolve
`/login` → `login.html` e `/chat` → `chat.html`; sem isso, um acesso direto a essas rotas
daria 404.

## Limitações conhecidas

- **Sem testes automatizados de front** (o backend cobre a API). Um próximo passo natural
  seria Testing Library para os componentes e Playwright para o fluxo completo.
- **Sem upload de arquivos, reações ou respostas** — seguem fora do escopo.
- **Busca limitada**: a barra lateral filtra por nome, e a busca dentro da conversa só
  alcança as mensagens já carregadas — não há busca no servidor.
- **"Excluir conta" no perfil é simulação**: o backend não tem rota de remoção de usuário.
