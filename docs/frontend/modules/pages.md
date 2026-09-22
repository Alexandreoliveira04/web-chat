# Páginas (`app/`)

Rotas do App Router. Todas são `"use client"`: o build é um
[export estático](../architecture.md#stack) e não há renderização no servidor.

| Rota | Arquivo | Papel |
| ---- | ------- | ----- |
| — | `app/layout.tsx` | casca HTML: `lang="pt-BR"`, fonte Geist, tema escuro, metadados |
| `/` | `app/page.tsx` | redireciona para `/chat` ou `/login` conforme o token |
| `/login` | `app/login/page.tsx` | login e cadastro no mesmo formulário |
| `/chat` | `app/chat/page.tsx` | tela principal: estado, tempo real e ações |

## `/` — porta de entrada

Só redireciona: se há token no `localStorage`, vai para `/chat`; senão, para `/login`.
Enquanto o efeito não roda, mostra "Carregando...". A checagem acontece no cliente porque o
token nunca está disponível no momento do build.

## `/login` — entrar e cadastrar

Um único formulário com dois modos (`login` | `register`), alternados por um botão. No modo
cadastro aparece o campo **Nome**.

- **Entrar** chama `authApi.login`, que já guarda o token, e vai para `/chat`.
- **Cadastrar** chama `authApi.register`, volta para o modo login e avisa
  "Conta criada. Faça login para continuar." — o registro não autentica automaticamente,
  então a senha é digitada de novo em uma tela que o usuário já conhece.

As restrições dos inputs (`required`, `minLength` 2/6, `maxLength` 100) espelham as
validações do backend, e a mensagem de erro vem da API: o `RequestError` concatena o mapa
`fields` do `ApiError`, então o usuário vê o motivo real da recusa.

## `/chat` — tela principal

É a única página com estado relevante, e ela é a dona dele:

| Estado | Guarda |
| ------ | ------ |
| `me`, `users` | usuário autenticado e lista de colaboradores |
| `chats`, `activeChatId` | conversas e qual está aberta |
| `messages`, `hasMore`, `nextBefore` | histórico da conversa aberta e cursor de paginação |
| `conectado`, `erro`, `carregando` | estado da conexão STOMP, erro em toast e carregamento |
| `mostrarNovoGrupo`, `mostrarParticipantes` | modais abertos |

Os componentes abaixo dela são de apresentação: recebem dados e callbacks por props. Um
único dono de estado evita sincronizar a mesma conversa em dois lugares — que é exatamente o
que uma mensagem chegando por WebSocket faria, se lista e janela tivessem cópias próprias.

### Ciclo de vida

1. Sem token → `/login`.
2. Com token → `usersApi.me()`, depois `usersApi.list()` e `chatsApi.list()` em paralelo.
3. `connectRealtime(...)` abre o STOMP e registra os handlers.
4. Ao desmontar, `client.deactivate()`.

Além dos handlers de tempo real (tabela em
[arquitetura](../architecture.md#tempo-real)), a página mantém dois listeners globais:

- `visibilitychange`: ao voltar para a aba, marca como lida a conversa aberta se houver
  pendência — mensagem que chegou em segundo plano não conta como lida;
- `keydown` (Esc): fecha a conversa aberta, desde que nenhum modal esteja aberto.

### Refs ao lado do estado

`meRef` e `activeChatIdRef` existem porque os handlers do STOMP são registrados uma vez e
capturariam o valor do primeiro render. As refs dão a eles o valor atual — é o que faz uma
mensagem cair na conversa certa depois de o usuário trocar de conversa.

### Ações

| Ação | O que faz |
| ---- | --------- |
| Abrir conversa | carrega histórico; se `unreadCount > 0`, marca como lida (otimista: zera o contador antes da resposta) |
| Iniciar conversa direta | `POST /chats` (cria ou reaproveita) e abre |
| Enviar | tenta `sendOverWebSocket`; sem conexão, cai para o `POST` REST e insere a mensagem localmente |
| Carregar anteriores | `messagesApi.history(chatId, nextBefore)`, concatenando no início |
| Editar / apagar | `PATCH`/`DELETE`; a tela só muda quando o evento volta em `/user/queue/message-updates` |
| Criar grupo, renomear, add/remover participante, sair | chamam a API e recarregam a conversa afetada |

Todo erro de ação passa por `executar(...)`, que exibe a mensagem em um toast no rodapé e
relança — assim o componente que disparou a ação também sabe que ela falhou.

Enquanto `me` é nulo, a página renderiza um *skeleton* com a mesma silhueta da interface
final (barra lateral, cabeçalho, bolhas), em vez de um spinner. Sem conversa selecionada,
mostra a tela de boas-vindas com o mascote.
