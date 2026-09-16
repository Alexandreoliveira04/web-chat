# Frontend

**Diretório:** `frontend/`
**Fase:** 7
**Situação:** implementado — substituiu as páginas estáticas que ficavam em
`src/main/resources/static`

Interface web do Web Chat: login, cadastro, conversas individuais, grupos, envio, edição e
exclusão de mensagens, leitura e tempo real.

## Stack

| Item | Escolha |
| ---- | ------- |
| Framework | Next.js 16 (App Router) |
| Linguagem | TypeScript |
| Estilos | Tailwind CSS 4 |
| Tempo real | `@stomp/stompjs` (mesmo protocolo do backend) |
| Build | `output: "export"` — HTML/JS estáticos, sem servidor Node em produção |

> **Decisão: export estático.** A aplicação é inteiramente client-side (o token fica no
> navegador e todas as chamadas são REST/WebSocket autenticadas com ele), então não há ganho
> em renderizar no servidor. Com o export, o Spring Boot continua sendo o único processo a
> subir, e o deploy segue sendo um único artefato.

## Estrutura

```text
frontend/
├── app/
│   ├── layout.tsx          tema escuro e metadados
│   ├── page.tsx            redireciona para /login ou /chat conforme o token
│   ├── login/page.tsx      login e cadastro no mesmo formulário
│   └── chat/page.tsx       tela principal: estado, efeitos e handlers de tempo real
├── components/
│   ├── Sidebar.tsx         conversas, busca, colaboradores sem conversa, novo grupo
│   ├── ChatWindow.tsx      cabeçalho, histórico, "carregar anteriores" e composer
│   ├── MessageItem.tsx     bolha da mensagem, ✓/✓✓, editar e apagar
│   ├── NewGroupModal.tsx   criação de grupo
│   ├── GroupSettingsModal.tsx  renomear, adicionar, remover e sair
│   ├── Avatar.tsx, Modal.tsx
├── lib/
│   ├── api.ts              cliente REST tipado e armazenamento do token
│   ├── realtime.ts         conexão STOMP e assinaturas
│   ├── types.ts            espelho dos DTOs da API
│   └── format.ts           título, prévia, iniciais e datas
└── next.config.ts          output: "export"
```

## Como rodar

**Desenvolvimento** (backend em `:8080`, front em `:3000`, com recarga automática):

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd ../frontend && npm install && npm run dev
```

O `.env.development` aponta `NEXT_PUBLIC_API_URL` para `http://localhost:8080/api/v1`. O
backend libera essa origem via `CORS_ALLOWED_ORIGINS` e `WS_ALLOWED_ORIGINS`.

**Build servido pelo backend** (mesma origem, sem CORS):

```bash
cd frontend && npm run build          # gera frontend/out
cd ../backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O `pom.xml` declara `frontend/out` como um `<resource>` com `targetPath` `static`, então o
Maven empacota o front junto com o jar — não existe passo de cópia nem diretório gerado
dentro de `src/`. Se `frontend/out` não existir, o Maven apenas avisa e a aplicação sobe só
com a API.

Sem `NEXT_PUBLIC_API_URL`, o cliente usa o caminho relativo `/api/v1` e deriva o WebSocket de
`window.location` — por isso o mesmo build funciona em qualquer host.

No `Dockerfile`, um estágio Node gera `frontend/out` e o estágio Maven o empacota da mesma
forma.

## Autenticação

O token JWT fica no `localStorage` e é enviado em `Authorization` nas chamadas REST e no frame
`CONNECT` do STOMP. Qualquer resposta `401` limpa o token e manda o usuário para `/login`; o
mesmo acontece se o servidor recusar a autenticação do WebSocket.

> **Limitação conhecida.** Guardar o token no `localStorage` o expõe a um eventual XSS. A
> alternativa (cookie `HttpOnly` + CSRF) exigiria sessão no servidor ou refresh token, que
> estão fora do escopo. O React escapa todo o conteúdo renderizado por padrão, e o projeto
> não usa `dangerouslySetInnerHTML` em lugar nenhum.

## Tempo real

`lib/realtime.ts` assina os quatro destinos do backend (ver
[chat](chat.md#websocket)) e a página `/chat` reage assim:

| Evento | Efeito na interface |
| ------ | ------------------- |
| `/user/queue/messages` | mensagem entra na conversa aberta; nas outras, incrementa o contador e atualiza a prévia; conversa desconhecida (grupo novo) é recarregada |
| `/user/queue/message-updates` | substitui a mensagem editada, ou mostra "mensagem apagada" |
| `/user/queue/read` | atualiza o ✓✓; se quem leu foi o próprio usuário em outra aba, zera o contador |
| `/user/queue/chats` | `CREATED`/`UPDATED` recarregam a conversa; `REMOVED` tira o grupo da lista |
| `/topic/presence` | atualiza o ponto verde no avatar e o "online/offline" no cabeçalho |

Envio: com o WebSocket conectado, a mensagem é publicada em `/app/chats/{id}/messages` e
aparece quando o servidor a devolve — um único caminho de renderização, igual para todas as
abas. Sem conexão, cai para o `POST` REST. Ids repetidos são ignorados.

Na reconexão (a cada 5 s), a lista de conversas e o histórico aberto são recarregados, para
recuperar o que chegou enquanto a conexão esteve fora.

## Leitura

Ao abrir uma conversa com não lidas, o front chama `PATCH .../messages/read`. Se a aba estiver
em segundo plano quando a mensagem chegar, a marcação espera o usuário voltar
(`visibilitychange`). O ✓✓ usa `lastReadByOthersMessageId`, então em grupo só aparece quando
**todos** os outros participantes leram.

## Pendências

- **Sem testes automatizados de front** (o backend cobre a API). Um próximo passo natural
  seria Testing Library para os componentes e Playwright para o fluxo completo.
- **Sem upload de arquivos, reações ou respostas** — seguem fora do escopo do projeto.
- A busca da barra lateral filtra por nome; não busca dentro das mensagens.
