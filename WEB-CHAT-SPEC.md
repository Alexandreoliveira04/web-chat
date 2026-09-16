# Web Chat — Especificação do Projeto

**Versão:** 1.0  
**Status:** Inicial  
**Backend:** Java 17 + Spring Boot + Maven  
**Frontend:** Next.js (planejado)  
**Banco:** PostgreSQL  
**Ambiente local:** Docker Compose  
**Deploy futuro:** Google Cloud

---

## 1. Visão geral

O Web Chat é uma aplicação web de comunicação interna entre colaboradores de uma organização.

O sistema permitirá que usuários autenticados encontrem outros colaboradores, iniciem conversas individuais e troquem mensagens em tempo real.

O projeto será desenvolvido como um **monólito modular**, utilizando **Spring Boot**, **MVC**, **REST API** e **WebSocket**.

O frontend será desenvolvido posteriormente em Next.js. Nesta etapa, o foco principal é o backend.

---

## 2. Objetivo

Construir uma aplicação simples de chat interno que demonstre:

- desenvolvimento de uma API REST;
- arquitetura MVC;
- organização modular;
- autenticação e autorização;
- persistência em PostgreSQL;
- comunicação em tempo real com WebSocket;
- execução local com Docker;
- preparação para deploy em cloud.

O projeto deve priorizar simplicidade, clareza e facilidade de manutenção, evitando overengineering.

---

## 3. Escopo do MVP

### 3.1 Autenticação

O sistema deverá permitir:

- cadastro de usuário;
- login;
- autenticação utilizando JWT;
- acesso somente a recursos autenticados;
- identificação do usuário autenticado;
- autorização por papéis (`USER` e `ADMIN`).

### 3.2 Usuários

O sistema deverá permitir:

- listar colaboradores;
- consultar um colaborador;
- consultar o próprio perfil;
- atualizar dados básicos do próprio perfil;
- representar o status do usuário como online/offline;
- administradores atualizarem dados básicos de qualquer colaborador;
- administradores alterarem o papel de outros colaboradores.

### 3.3 Conversas

O sistema deverá permitir:

- iniciar uma conversa individual;
- listar as conversas do usuário autenticado;
- consultar os participantes de uma conversa;
- consultar o histórico de mensagens;
- criar conversas em grupo, com nome e vários participantes;
- renomear o grupo, adicionar e remover participantes (somente quem criou);
- sair de um grupo.

O MVP entregue (fases 1–6) teve apenas conversas individuais; os grupos entraram na
ampliação de escopo da Fase 9.

### 3.4 Mensagens

O sistema deverá permitir:

- enviar mensagens;
- receber mensagens em tempo real;
- armazenar mensagens no banco;
- consultar histórico;
- identificar remetente;
- armazenar data/hora;
- marcar mensagens como lidas, de forma independente para cada participante;
- editar a própria mensagem;
- apagar a própria mensagem.

---

## 4. Fora do escopo inicial

Os seguintes recursos não fazem parte do MVP:

- chamadas de áudio;
- chamadas de vídeo;
- envio de arquivos;
- envio de imagens;
- respostas/threads;
- reações;
- busca avançada;
- notificações push;
- Redis;
- Kafka;
- RabbitMQ;
- microsserviços;
- Kubernetes;
- CQRS;
- Event Sourcing;
- arquitetura hexagonal;
- DDD complexo.

Esses recursos poderão ser adicionados posteriormente caso sejam necessários.

**Ampliação de escopo (pós-MVP).** Grupos, edição e exclusão de mensagens saíram desta
lista e passaram a fazer parte do escopo, junto com o frontend Next.js da Fase 7. As fases
9 e 10 detalham o que muda.

---

## 5. Stack tecnológica

### Backend

- Java 17
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT
- Bean Validation
- WebSocket
- PostgreSQL
- Docker / Docker Compose

Spring Boot 3 possui Java 17 como requisito mínimo. A versão exata do Spring Boot deverá ser mantida consistente no `pom.xml` durante o desenvolvimento. 

### Frontend

- Next.js 16 (App Router)
- TypeScript
- Tailwind CSS
- `@stomp/stompjs` para o tempo real

Implementado na Fase 7, em `frontend/`, com **export estático**: o build gera HTML/JS que o
próprio Spring Boot serve, mantendo um único artefato de deploy. Detalhes em
[docs/frontend.md](docs/frontend.md).

### Infraestrutura

Local:

- Docker Compose
- PostgreSQL

Futuro:

- Google Cloud
- PostgreSQL gerenciado ou outra solução compatível
- aplicação Spring Boot containerizada

---

## 6. Arquitetura

A aplicação será um **monólito modular**.

Não serão criados múltiplos serviços ou múltiplos projetos Maven.

Estrutura conceitual:

```text
Next.js
   |
   | REST / WebSocket
   v
Spring Boot
   |
   +-- auth
   +-- user
   +-- chat
   +-- shared
   |
   v
PostgreSQL
```

---

## 7. Padrão MVC

Cada módulo deverá seguir uma organização baseada em MVC.

Fluxo esperado:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
Database
```

### Controller

Responsável por:

- receber requisições;
- validar entrada;
- chamar serviços;
- devolver respostas HTTP.

Controllers não devem conter regras de negócio.

### Service

Responsável por:

- regras de negócio;
- validações de negócio;
- coordenação das operações;
- transações.

### Repository

Responsável pelo acesso aos dados utilizando Spring Data JPA.

### Entity

Representa as entidades persistidas no banco.

### DTO

Responsável pela comunicação entre API e cliente.

Entidades JPA não devem ser expostas diretamente nas respostas da API.

---

## 8. Módulos

### 8.1 AUTH

Responsável pela autenticação e pela autorização.

Responsabilidades:

- registro;
- login;
- geração de JWT;
- validação do JWT;
- integração com Spring Security;
- regras de acesso por papel.

Estrutura:

```text
auth/
├── config/       SecurityConfig, SecurityErrorHandler
├── controller/
├── dto/
├── filter/       JwtAuthenticationFilter
├── jwt/          JwtService
└── service/
```

---

### 8.2 USER

Responsável pelos colaboradores.

Estrutura:

```text
user/
├── config/       AdminInitializer (administrador inicial)
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

Responsabilidades:

- cadastro;
- consulta;
- atualização;
- perfil;
- status;
- papel (`USER` / `ADMIN`).

---

### 8.3 CHAT

Responsável por conversas e mensagens.

Estrutura:

```text
chat/
├── controller/   ChatController, MessageController
├── dto/
├── entity/       Chat, ChatType, ChatParticipant, Message
├── repository/   ChatRepository, MessageRepository
├── service/      ChatService, ChatCreator, MessageService
└── websocket/    WebSocketConfig, StompAuthInterceptor, ChatWebSocketController, ChatEventBroadcaster, PresenceService
```

Responsabilidades:

- conversas;
- participantes;
- mensagens;
- histórico;
- WebSocket.

---

### 8.4 SHARED

Deve conter somente componentes realmente compartilhados.

Estrutura:

```text
shared/
├── config/
└── exception/
```

Exemplos:

- tratamento global de exceções;
- configurações comuns;
- respostas de erro.

Evitar transformar `shared` em um depósito de classes sem responsabilidade clara.

---

## 9. Estrutura de diretórios

```text
web-chat/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── br/
│   │   │   │       └── edu/
│   │   │   │           └── webchat/
│   │   │   │               ├── WebChatApplication.java
│   │   │   │               │
│   │   │   │               ├── auth/
│   │   │   │               ├── user/
│   │   │   │               ├── chat/
│   │   │   │               └── shared/
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── application-dev.yml
│   │   │
│   │   └── test/
│   │
│   ├── pom.xml
│   └── mvnw, mvnw.cmd, .mvn/
│
├── frontend/                    (Next.js: app, components, lib)
├── docs/
├── compose.yaml
├── Dockerfile
├── README.md
├── WEB-CHAT-SPEC.md
└── .gitignore
```

O package raiz deverá ser ajustado caso o projeto existente utilize outro namespace.

**Decisão de organização.** Até a Fase 7 o projeto Maven ficava na raiz. Com a chegada do
frontend, backend e frontend passaram a ser duas pastas irmãs (`backend/` e `frontend/`),
cada uma com as próprias ferramentas de build. Continua sendo **um único projeto Maven** e um
único artefato de deploy, como manda a seção 6.

---

## 10. Modelo de dados

### users

```text
id
name
email
password
status
role
created_at
updated_at
```

Regras:

- `id` deve ser gerado automaticamente;
- `email` deve ser único;
- `password` deve ser armazenada somente com hash;
- `status` representa o estado atual do usuário;
- `role` é o papel do usuário (`USER` ou `ADMIN`), com padrão `USER`.

### chats

```text
id
direct_key
created_at
updated_at
```

Regras:

- `direct_key` identifica o par de usuários da conversa individual, com o menor id primeiro (ex.: `3:4`);
- `direct_key` é único: existe no máximo uma conversa por par de usuários, garantido pelo banco.

### chat_participants

```text
chat_id
user_id
last_read_message_id
joined_at
```

Regras:

- um chat individual possui exatamente dois participantes; um grupo possui de um a cinquenta;
- um usuário não pode participar duas vezes da mesma conversa (chave primária composta `(chat_id, user_id)`);
- entre o mesmo par de usuários existe uma única conversa (`chats.direct_key` único): uma nova tentativa, inclusive simultânea, reaproveita a existente;
- `last_read_message_id` é a última mensagem lida por **aquele** participante; nulo enquanto ele não leu nada.

> **Decisão da Fase 9 (substitui a decisão da Fase 4).** Até a Fase 6, `chat_participants`
> era apenas uma tabela de junção (`@ManyToMany`), sem colunas próprias, e a leitura ficava
> em `messages.read_at`. Isso não funciona em grupo: o primeiro participante que abrisse a
> conversa marcaria as mensagens como lidas para todos, zerando o contador dos demais.
> Com a leitura por participante, `chat_participants` passou a ter colunas próprias e virou
> a entidade `ChatParticipant`, como a versão original desta spec previa.

### messages

```text
id
chat_id
sender_id
content
created_at
edited_at
deleted_at
```

Regras:

- uma mensagem pertence a uma conversa;
- uma mensagem possui exatamente um remetente, sempre o usuário autenticado que a enviou;
- somente participantes da conversa enviam e leem mensagens;
- o conteúdo não pode ser vazio nem só espaços (validado na API e por `CHECK` no banco) e tem no máximo 2000 caracteres;
- espaços nas pontas do conteúdo são removidos antes de gravar;
- `created_at` é preenchido automaticamente;
- a leitura não fica na mensagem: cada participante guarda a própria em `chat_participants.last_read_message_id`;
- `edited_at` é preenchido quando o autor edita a mensagem; nulo enquanto não houver edição;
- `deleted_at` é preenchido quando o autor apaga a mensagem; o conteúdo é esvaziado e a mensagem permanece no histórico marcada como apagada;
- somente o autor edita ou apaga a própria mensagem, e nunca uma já apagada;
- enviar uma mensagem atualiza `chats.updated_at` (última atividade da conversa).

---

## 11. Relacionamentos

```text
User
 |
 +----< ChatParticipant >---- Chat
                                  |
                                  +----< Message
                                           |
                                           +---- User (sender)
```

Relacionamentos principais:

- Chat 1:N ChatParticipant (no JPA: `Chat.participants`, `@OneToMany` com `orphanRemoval`)
- User 1:N ChatParticipant
- ChatParticipant N:1 Message (a última mensagem lida por aquele participante)
- Chat 1:N Message
- User 1:N Message (remetente)
- Chat N:1 User (dono do grupo; nulo em conversa individual)

---

## 12. API REST

Prefixo:

```text
/api/v1
```

### Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

### Users

```http
GET   /api/v1/users
GET   /api/v1/users/{id}
GET   /api/v1/users/me
PUT   /api/v1/users/me
PUT   /api/v1/users/{id}          (ADMIN)
PATCH /api/v1/users/{id}/role     (ADMIN)
```

### Chats

```http
GET    /api/v1/chats
POST   /api/v1/chats
POST   /api/v1/chats/groups
GET    /api/v1/chats/{chatId}
PATCH  /api/v1/chats/{chatId}
POST   /api/v1/chats/{chatId}/participants
DELETE /api/v1/chats/{chatId}/participants/{userId}
DELETE /api/v1/chats/{chatId}/participants/me
GET    /api/v1/chats/{chatId}/messages?before={messageId}&size={1-100}
POST   /api/v1/chats/{chatId}/messages
PATCH  /api/v1/chats/{chatId}/messages/{messageId}
DELETE /api/v1/chats/{chatId}/messages/{messageId}
PATCH  /api/v1/chats/{chatId}/messages/read
```

`POST /api/v1/chats` recebe `{ "participantId": <id> }` e responde `201` com `Location`
quando cria a conversa, ou `200` com a conversa já existente entre os dois usuários.
Qualquer endpoint com `{chatId}` de uma conversa da qual o usuário não participa resulta em
`403`, inclusive para `ADMIN`.

Decisões da Fase 5:

- **listagem de conversas** (`GET /chats`, `GET /chats/{chatId}`) traz `lastMessage` e
  `unreadCount` (mensagens do outro participante ainda não lidas), calculados na consulta
  a partir de `messages`, sem colunas denormalizadas em `chats`;
- **histórico paginado por cursor**: sem `before`, as `size` mensagens mais recentes (padrão
  50); com `before`, as anteriores ao id informado. A resposta traz as mensagens em ordem
  cronológica, `hasMore` e `nextBefore`. Cursor em vez de offset para que mensagens novas
  não desloquem as páginas;
- **leitura explícita**: `PATCH /chats/{chatId}/messages/read` marca como lidas todas as
  mensagens recebidas ainda não lidas e devolve a quantidade; o `GET` do histórico não
  altera dados;
- `POST /chats/{chatId}/messages` recebe `{ "content": "..." }` e responde `201` com a
  mensagem criada.

Os endpoints poderão evoluir conforme a implementação.

---

## 13. WebSocket

O WebSocket será utilizado somente para comunicação em tempo real.

REST será utilizado para:

- autenticação;
- consulta de usuários;
- criação/listagem de chats;
- histórico;
- operações persistentes.

WebSocket será utilizado para:

- envio de mensagens em tempo real;
- recebimento de mensagens;
- eventos de chat.

A implementação deverá utilizar uma abordagem compatível com o ecossistema Spring, preferencialmente STOMP sobre WebSocket.

### 13.1 Protocolo (Fase 6)

Implementado com **STOMP sobre WebSocket nativo** e o broker simples em memória do Spring.
Referência completa em [docs/chat.md](docs/chat.md#websocket).

| Item | Valor |
| ---- | ----- |
| Endpoint | `/ws` (handshake HTTP público; origens restritas por `WS_ALLOWED_ORIGINS`) |
| Autenticação | `Authorization: Bearer <jwt>` no frame `CONNECT`; token inválido → frame `ERROR` e conexão encerrada |
| Envio | `SEND /app/chats/{chatId}/messages` com `{ "content": "..." }` |
| Mensagens recebidas | `/user/queue/messages` — `MessageResponse`, para os dois participantes |
| Leitura | `/user/queue/read` — `{ chatId, readerId, lastReadMessageId, markedAsRead }`, para os participantes |
| Erros do envio | `/user/queue/errors` — `ApiError`, só para quem enviou |
| Mensagem editada/apagada | `/user/queue/message-updates` — `MessageResponse` com `editedAt`/`deletedAt`, para os participantes |
| Conversas | `/user/queue/chats` — `{ event, chatId }` (`CREATED`/`UPDATED`/`REMOVED`), para os participantes afetados |
| Presença | `/topic/presence` — `{ userId, status }`, para todos os conectados |

Regras:

- envio por WebSocket e por `POST /chats/{chatId}/messages` passam pelo mesmo service, com as
  mesmas validações; os dois caminhos notificam em tempo real;
- as notificações são enviadas somente **após o commit** da transação;
- destinos por usuário (`/user/queue/...`) em vez de um tópico por conversa: uma assinatura
  recebe todas as conversas do usuário e o Spring entrega apenas às sessões dele;
- o cliente só pode assinar os quatro destinos acima e só pode enviar para `/app/...`;
- `users.status` é `ONLINE` enquanto o usuário tiver ao menos uma conexão aberta e volta a
  `OFFLINE` quando a última fecha; na inicialização todos são marcados `OFFLINE`.

---

## 14. Autenticação

O sistema utilizará JWT.

Fluxo:

```text
Cliente
   |
   | POST /auth/login
   v
Spring Boot
   |
   | valida credenciais
   v
JWT
   |
   v
Cliente
```

Nas requisições protegidas:

```http
Authorization: Bearer <token>
```

Senhas nunca devem ser armazenadas em texto puro.

### 14.1 Autorização por papéis

Decisão tomada no Módulo 0, para corrigir a falha em que qualquer usuário autenticado
podia alterar os dados de outro colaborador.

Papéis:

| Papel | Permissões |
| ----- | ---------- |
| `USER` | ler colaboradores, ler e atualizar o **próprio** perfil (`/users/me`), usar o chat |
| `ADMIN` | tudo de `USER`, mais atualizar qualquer colaborador e alterar papéis |

Regras:

- cada usuário possui **um único papel**, armazenado na coluna `users.role`;
- o cadastro público (`POST /auth/register`) cria sempre `USER`; o papel nunca vem do corpo da requisição;
- as regras de acesso por rota ficam no `SecurityConfig` (`hasRole("ADMIN")`); violação resulta em **403**;
- o papel **não** é gravado no JWT: ele é lido do banco a cada requisição, então uma mudança de papel vale imediatamente;
- nenhum usuário pode alterar o próprio papel (403), o que impede que o sistema fique sem administrador;
- o administrador inicial é criado (ou promovido) na inicialização a partir de `ADMIN_EMAIL`, `ADMIN_PASSWORD` e `ADMIN_NAME`; sem `ADMIN_EMAIL`, nada é criado.

Administradores **não** ganham acesso a conversas de terceiros: a regra de participante do chat vale para todos os papéis.

---

## 15. Validação

Utilizar Bean Validation para validações de entrada.

Exemplos:

- nome obrigatório;
- email válido;
- senha com tamanho mínimo;
- mensagem não vazia;
- IDs obrigatórios.

As regras de negócio devem permanecer nos services.

---

## 16. Tratamento de erros

A API deverá possuir tratamento global de exceções.

Erros devem retornar respostas HTTP adequadas.

Exemplos:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
500 Internal Server Error
```

As respostas de erro devem possuir estrutura consistente.

---

## 17. Configuração

As configurações específicas de ambiente não devem ficar hardcoded.

Exemplos:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
JWT_SECRET
ADMIN_NAME
ADMIN_EMAIL
ADMIN_PASSWORD
WS_ALLOWED_ORIGINS
CORS_ALLOWED_ORIGINS
```

O arquivo `application.yml` deverá utilizar variáveis de ambiente quando apropriado.

Nunca versionar:

- senhas reais;
- secrets;
- tokens;
- credenciais da Google Cloud.

---

## 18. PostgreSQL

O banco de desenvolvimento deverá ser executado através do Docker Compose.

Exemplo:

```yaml
services:
  postgres:
    image: postgres:16
    container_name: web-chat-postgres
    environment:
      POSTGRES_DB: webchat
      POSTGRES_USER: webchat
      POSTGRES_PASSWORD: webchat
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

A configuração poderá ser ajustada de acordo com o ambiente local.

---

## 19. Banco de dados e migrations

O projeto deverá utilizar uma ferramenta de migrations, preferencialmente Flyway.

As migrations devem ser versionadas no Git.

Migrations atuais e previstas:

```text
backend/src/main/resources/db/migration/
├── V1__init.sql                          (aplicada)
├── V2__create_users.sql                  (aplicada)
├── V3__add_role_to_users.sql             (aplicada)
├── V4__create_chats.sql                  (aplicada)
├── V5__create_chat_participants.sql      (aplicada)
├── V6__add_direct_key_to_chats.sql       (aplicada)
├── V7__create_messages.sql               (aplicada)
├── V8__add_read_state_to_participants.sql (aplicada — leitura por participante)
├── V9__add_groups_to_chats.sql           (aplicada — grupos)
└── V10__add_edit_and_delete_to_messages.sql (aplicada — editar/apagar)
```

Migrations já aplicadas nunca devem ser editadas; qualquer mudança de schema entra em uma nova versão.

O Hibernate não deverá ser utilizado como mecanismo principal de versionamento do schema em produção.

---

## 20. Testes

O backend deverá possuir testes básicos.

Prioridade:

1. testes unitários dos services;
2. testes dos controllers;
3. testes de repository quando necessário;
4. testes de integração dos principais fluxos.

Fluxos mínimos:

- cadastro;
- login;
- criação de chat;
- envio de mensagem;
- consulta de histórico.

Os cinco fluxos são cobertos de ponta a ponta por `MvpFlowIntegrationTest`, que sobe o
servidor em porta real e percorre cadastro → login → criação da conversa → envio → histórico
→ leitura, verificando também que endpoint protegido exige token e que quem não participa
recebe 403. O tempo real é coberto por `RealtimeIntegrationTest`, com cliente STOMP real.

---

## 21. Docker

O projeto deverá possuir:

- `compose.yaml` para PostgreSQL;
- `Dockerfile` para o backend.

Durante o desenvolvimento inicial, não é obrigatório executar o Spring Boot dentro do Docker.

A prioridade é manter o banco containerizado e o backend executando pela IDE/Maven.

### Implementado (Módulo 4)

- **`Dockerfile` multi-stage**: estágio Node (`node:22-alpine`) gera o export do frontend,
  estágio Maven (`maven:3.9-eclipse-temurin-17`, com dependências em camada separada do
  código) empacota o jar já com o front em `static/` — o `pom.xml` declara `frontend/out`
  como recurso —, e o runtime é `eclipse-temurin:17-jre-alpine` executando como usuário sem
  privilégios.
- **`compose.yaml`**: o serviço `app` fica no profile `app`, então `docker compose up -d`
  continua subindo apenas o banco (fluxo de desenvolvimento) e
  `docker compose --profile app up -d --build` sobe banco + aplicação. O `app` espera o
  healthcheck do PostgreSQL e tem o próprio healthcheck em `/api/v1/health`.
- Toda a configuração do container vem de variáveis de ambiente, com padrões descartáveis
  apenas para uso local.

---

## 22. Deploy futuro

O sistema deverá ser preparado para deploy futuro na Google Cloud.

Requisitos:

- configuração via environment variables;
- aplicação stateless;
- build Maven reproduzível;
- Dockerfile funcional;
- nenhuma dependência de caminhos locais;
- nenhuma credencial armazenada no código.

O serviço exato da Google Cloud será definido posteriormente.

**Restrição conhecida (Fase 6):** o broker STOMP simples e o controle de presença ficam em
memória, então o tempo real funciona corretamente com **uma única instância** da aplicação.
Escalar para várias instâncias exigirá um broker externo (ex.: RabbitMQ via
`enableStompBrokerRelay`) e presença compartilhada — tecnologias hoje fora do escopo (§4). O
REST continua stateless.

---

## 23. Requisitos não funcionais

### RNF01 — Manutenibilidade

O código deve ser simples e organizado por módulos.

### RNF02 — Segurança

Senhas devem ser armazenadas com hash e endpoints protegidos devem exigir autenticação.

### RNF03 — Performance

Consultas devem evitar carregamentos desnecessários e N+1 quando possível.

### RNF04 — Configuração

Dados específicos do ambiente devem ser configuráveis por variáveis de ambiente.

### RNF05 — Compatibilidade

O backend deve utilizar Java 17.

### RNF06 — Execução

O projeto deve ser executável através do Maven.

### RNF07 — Persistência

Os dados devem permanecer armazenados no PostgreSQL.

---

## 24. Convenções

### Classes

```text
UserController
UserService
UserRepository
UserEntity
UserResponse
UserRequest
```

### Endpoints

Utilizar nomes no plural:

```text
/users
/chats
/messages
```

### IDs

Numéricos: `Long` no Java e `BIGINT` no PostgreSQL, gerados pelo banco
(`GENERATED BY DEFAULT AS IDENTITY` / `GenerationType.IDENTITY`).

Decisão tomada na Fase 2, em favor da simplicidade do projeto. UUID não é
utilizado em nenhuma entidade.

### Datas

Utilizar tipos modernos do Java, como `Instant` ou `OffsetDateTime`, evitando `java.util.Date`.

### JSON

Utilizar `camelCase`.

Exemplo:

```json
{
  "id": 1,
  "createdAt": "2026-09-08T22:00:00Z"
}
```

---

## 25. Roadmap

### Fase 1 — Fundação

- [x] revisar projeto Maven existente;
- [x] configurar Java 17;
- [x] configurar Spring Boot;
- [x] configurar PostgreSQL;
- [x] configurar Docker Compose;
- [x] configurar Flyway;
- [x] organizar pacotes.

### Fase 2 — USER

- [x] entidade User;
- [x] migration;
- [x] repository;
- [x] service;
- [x] DTOs;
- [x] controller;
- [x] validações;
- [x] testes.

### Fase 3 — AUTH

- [x] registro;
- [x] BCrypt;
- [x] login;
- [x] JWT;
- [x] Spring Security;
- [x] proteção dos endpoints;
- [x] testes.

### Fase 3.1 — PAPÉIS E PERMISSÕES (Módulo 0)

- [x] papel `USER` / `ADMIN` na entidade User;
- [x] migration `V3__add_role_to_users.sql`;
- [x] `POST /auth/register` substitui `POST /users`;
- [x] `PUT /users/me` para o próprio perfil;
- [x] `PUT /users/{id}` e `PATCH /users/{id}/role` restritos a ADMIN;
- [x] `ForbiddenException` (403);
- [x] administrador inicial via variáveis de ambiente;
- [x] testes.

### Fase 4 — CHAT

- [x] entidade Chat;
- [x] participantes;
- [x] migrations;
- [x] repositories;
- [x] services;
- [x] controllers;
- [x] criação de conversas;
- [x] listagem de conversas;
- [x] unicidade da conversa por par (`direct_key`);
- [x] testes.

### Fase 5 — MESSAGE

- [x] entidade Message;
- [x] migration;
- [x] envio;
- [x] histórico (paginação por cursor);
- [x] leitura;
- [x] última mensagem e não lidas na listagem de conversas;
- [x] integração das páginas estáticas com a API de conversas e mensagens;
- [x] testes.

### Fase 6 — WEBSOCKET

- [x] configuração;
- [x] conexão;
- [x] autenticação;
- [x] envio;
- [x] recebimento;
- [x] eventos (leitura e presença online/offline);
- [x] integração das páginas estáticas;
- [x] testes.

### Fase 7 — FRONTEND

- [x] projeto Next.js;
- [x] login;
- [x] usuários;
- [x] lista de chats;
- [x] tela de conversa;
- [x] WebSocket;
- [x] grupos e edição/exclusão de mensagens;
- [x] remoção das páginas estáticas.

### Fase 9 — GRUPOS

- [x] leitura por participante (`ChatParticipant` como entidade);
- [x] tipo, nome e dono da conversa;
- [x] criação de grupo;
- [x] renomear, adicionar e remover participantes (somente o dono);
- [x] sair do grupo, com transferência de posse e remoção do grupo vazio;
- [x] eventos de conversa no WebSocket;
- [x] testes.

### Fase 10 — EDITAR E APAGAR MENSAGENS

- [x] editar a própria mensagem (`edited_at`);
- [x] apagar a própria mensagem (`deleted_at`, conteúdo esvaziado);
- [x] eventos no WebSocket;
- [x] testes.

### Fase 8 — DEPLOY

- [x] Docker (`Dockerfile` + serviço `app` no `compose.yaml`);
- [ ] configuração de produção;
- [ ] Google Cloud;
- [ ] banco;
- [ ] variáveis de ambiente;
- [ ] domínio/HTTPS;
- [ ] monitoramento básico.

---

## 26. Critérios de conclusão do MVP

O MVP será considerado concluído quando:

- [x] um usuário puder ser cadastrado;
- [x] um usuário puder realizar login;
- [x] endpoints protegidos exigirem autenticação;
- [x] dois usuários puderem iniciar uma conversa;
- [x] mensagens forem persistidas no PostgreSQL;
- [x] o histórico puder ser consultado;
- [x] mensagens puderem ser recebidas em tempo real;
- [x] o backend puder ser executado localmente;
- [x] o PostgreSQL puder ser iniciado via Docker Compose;
- [x] testes básicos estiverem funcionando.

---

## 27. Princípios do projeto

1. **Simplicidade antes de abstração.**
2. **Não implementar funcionalidades fora do escopo sem necessidade.**
3. **Não adicionar tecnologias apenas por serem populares.**
4. **Controllers devem ser finos.**
5. **Regras de negócio devem ficar nos services.**
6. **Entidades não devem ser expostas diretamente pela API.**
7. **Configurações sensíveis devem ficar fora do código.**
8. **Toda alteração relevante deve manter o projeto compilando e testável.**
9. **Cada módulo deve possuir responsabilidade clara.**
10. **A arquitetura deve permanecer adequada ao tamanho do projeto.**

---

## 28. Estado atual

**MVP concluído.** Fases 1 a 6 implementadas — fundação, USER, AUTH com papéis (Módulo 0),
conversas (Módulo 1), mensagens via REST (Módulo 2) e tempo real via WebSocket (Módulo 3) —
mais o Módulo 4: `Dockerfile`, backend containerizado no `compose.yaml` e teste de ponta a
ponta do fluxo mínimo. Todos os critérios do §26 estão atendidos.

**Ampliação de escopo em andamento** (registrada aqui antes da implementação, conforme a
regra desta seção): grupos (Fase 9), edição e exclusão de mensagens (Fase 10) e o frontend
Next.js (Fase 7), que substituirá as páginas estáticas. A entrega é incremental, um módulo
por vez: leitura por participante → grupos → editar/apagar → frontend Next.js.
**Todas concluídas.** As páginas estáticas foram removidas; a interface agora é o projeto
`frontend/`, cujo build é servido pelo próprio Spring Boot.

O que permanece fora do escopo entregue, para eventual continuidade:

- **Fase 8 — deploy na Google Cloud.** O `Dockerfile` e a configuração por variáveis de
  ambiente já existem; faltam provisionar banco gerenciado, domínio/HTTPS e monitoramento.
  Antes de escalar para mais de uma instância, ver a restrição do §22 sobre o broker em
  memória.

Alterações arquiteturais ou funcionais relevantes devem ser registradas nesta especificação antes de serem implementadas.

O desenvolvimento deve ocorrer de forma incremental, seguindo o roadmap.
