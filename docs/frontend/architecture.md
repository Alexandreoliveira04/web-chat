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
├── components/             Sidebar, ChatWindow, MessageItem, painel de detalhes, chamada, modais, Avatar
├── lib/
│   ├── api.ts              cliente REST tipado e armazenamento do token
│   ├── realtime.ts         conexão STOMP, assinaturas e envio
│   ├── useVoiceCall.ts     chamada de voz: estado, microfone e WebRTC
│   ├── attachments.ts      reconhece fotos, vídeos e documentos nas mensagens
│   ├── ringtone.ts         toque da chamada, gerado com Web Audio
│   ├── types.ts            espelho dos DTOs da API
│   ├── format.ts           título, prévia, iniciais e datas
│   └── cropImage.ts        recorte da foto de perfil antes do envio
└── next.config.ts          output: "export"
```

O estado vive todo em `app/chat/page.tsx`; os componentes são de apresentação e recebem
dados e callbacks por props. Não há Redux, Zustand nem Context — uma tela só, com um dono
de estado, não justifica a camada extra. Detalhes em [páginas](modules/pages.md).

## Comunicação com o backend

Dois caminhos, ambos autenticados com o mesmo JWT:

| Caminho | Arquivo | Uso |
| ------- | ------- | --- |
| REST | [`lib/api.ts`](modules/libs.md#apits) | login, cadastro, perfil e foto, conversas, histórico, arquivos compartilhados, leitura, upload |
| STOMP | [`lib/realtime.ts`](modules/libs.md#realtimets) | receber mensagens, edições, ✓✓, eventos de grupo, presença, perfis e eventos de chamada; enviar mensagem e sinais da chamada |

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

`lib/realtime.ts` assina os destinos do backend (ver
[chat](../backend/modules/chat.md#websocket) e [call](../backend/modules/call.md#websocket))
e a página `/chat` reage assim:

| Evento | Efeito na interface |
| ------ | ------------------- |
| `/user/queue/messages` | mensagem entra na conversa aberta; nas outras, incrementa o contador e atualiza a prévia; conversa desconhecida (grupo novo) é recarregada |
| `/user/queue/message-updates` | substitui a mensagem editada, ou mostra "mensagem apagada" |
| `/user/queue/read` | atualiza o ✓✓; se quem leu foi o próprio usuário em outra aba, zera o contador |
| `/user/queue/chats` | `CREATED`/`UPDATED` recarregam a conversa; `REMOVED` tira o grupo da lista |
| `/topic/presence` | atualiza o ponto verde no avatar e o "online/offline" no cabeçalho |
| `/topic/profiles` | troca nome e foto de quem mudou o perfil, na lista, nas conversas e no próprio perfil |
| `/topic/users` | quem acabou de se cadastrar aparece em "Colaboradores" |
| `/user/queue/calls` | toca, atende ou encerra a chamada (ver [chamadas de voz](#chamadas-de-voz)) |
| `/user/queue/call-signal` | entrega a oferta, a resposta e os candidatos ICE ao WebRTC |
| `/user/queue/errors` | mostra o erro no toast do rodapé |

**Envio.** Com o WebSocket conectado, a mensagem é publicada em `/app/chats/{id}/messages` e
só aparece quando o servidor a devolve — um único caminho de renderização, igual para todas
as abas. Sem conexão, cai para o `POST` REST. Ids repetidos são ignorados.

**Reconexão** a cada 5 s. Ao reconectar, a lista de conversas e o histórico aberto são
recarregados, para recuperar o que chegou enquanto a conexão esteve fora.

**Aba em segundo plano.** Depois de 5 minutos com a aba escondida, e sem chamada em
andamento, a página desconecta o STOMP; ao voltar, reconecta e recarrega como numa queda.
No Cloud Run, uma conexão aberta mantém o servidor ligado (e contando no limite gratuito),
então uma aba esquecida não deve segurá-lo (ver [deploy](../deploy.md#trava-de-custo)).

## Chamadas de voz

O hook [`useVoiceCall`](modules/libs.md#usevoicecallts) controla a chamada inteira. O áudio
vai direto entre os navegadores por **WebRTC**; o backend só repassa os sinais.

```text
idle ─► outgoing (liguei, tocando do outro lado) ─► connecting ─► active
idle ─► incoming (estão me ligando)  ─► connecting ─► active
qualquer estado ─► idle (desligou, recusou, caiu, não atendeu em 30 s)
```

1. Quem liga pede o microfone (`getUserMedia`) **antes** de avisar o servidor. Se o acesso
   for negado, a chamada nem começa.
2. Quem recebe vê o modal `IncomingCall` e ouve o toque. Ao atender, pede o microfone e cria
   o `RTCPeerConnection`.
3. Depois do `ACCEPTED`, quem ligou cria a oferta SDP; quem atendeu responde. Os candidatos
   ICE que chegam antes da descrição remota ficam numa fila até ela chegar.
4. Quando o WebRTC conecta, a barra `CallBar` mostra "Voz conectada" e o tempo de chamada.

O estado da chamada fica na página `/chat`, e não dentro da conversa: assim dá para navegar
por outras conversas sem desligar. Se a conexão STOMP cair e voltar, a chamada é encerrada
no cliente, porque o servidor já a encerrou ao perder a sessão.

Para descobrir o próprio endereço público, o navegador usa o STUN público do Google. Não há
servidor TURN (ver [limitações](#limitações-conhecidas)).

> **Microfone exige contexto seguro.** O navegador só libera `getUserMedia` em HTTPS ou em
> `localhost`. Em outro host sem HTTPS, o botão de ligar mostra o aviso e não liga.

Para testar sozinho, abra a aplicação em `localhost:8080` com um usuário e em
`127.0.0.1:8080` com outro: são origens diferentes, então cada uma guarda o próprio token.
Use fone de ouvido para evitar microfonia.

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
- **Sem reações ou respostas** — seguem fora do escopo.
- **Chamadas só individuais e sem TURN**: atrás de redes restritivas o áudio pode não
  conectar.
- **Lint com erros antigos**: `npx eslint` acusa 3 erros que já existiam (uma ref atribuída
  durante o render, um `any` e um `@ts-ignore`). O `npm run build` passa mesmo assim.
- **Busca limitada**: a barra lateral filtra por nome, e a busca dentro da conversa só
  alcança as mensagens já carregadas — não há busca no servidor.
- **"Excluir conta" no perfil é simulação**: o backend não tem rota de remoção de usuário.
