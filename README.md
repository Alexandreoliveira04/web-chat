# Web Chat

Aplicação web de chat interno entre colaboradores.

Backend em Java 17 + Spring Boot 3, organizado como monólito modular seguindo MVC.
A especificação completa do projeto está em [WEB-CHAT-SPEC.md](WEB-CHAT-SPEC.md) e a
documentação por módulo em [docs/](docs/README.md).

> Estado atual: **MVP concluído** — cadastro, login com papéis, conversas, mensagens,
> histórico, leitura e tempo real, com backend containerizado. Fora do escopo entregue:
> frontend Next.js (fase 7) e deploy na Google Cloud (fase 8).

---

## Requisitos

- Java 17
- Maven (o wrapper `mvnw` já acompanha o projeto)
- Docker
- Docker Compose

---

## Banco

O PostgreSQL de desenvolvimento roda em container:

```bash
docker compose up -d
```

Isso sobe um PostgreSQL 16 em `localhost:5432` com banco, usuário e senha `webchat`
e um volume `postgres_data` para persistência.

Para parar:

```bash
docker compose down
```

---

## Tudo em containers (opcional)

Além do banco, o backend também pode rodar em container:

```bash
docker compose --profile app up -d --build
```

Isso constrói a imagem pelo `Dockerfile` e sobe banco + aplicação em
`http://localhost:8080`, já com o administrador de desenvolvimento
(`admin@webchat.local` / `admin123`). Sem o `--profile app`, o `docker compose up -d`
continua subindo só o banco.

```bash
docker compose --profile app logs -f app   # acompanhar o log
docker compose --profile app down          # parar tudo
```

Em qualquer outro ambiente, informe `JWT_SECRET`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` por
variável de ambiente: os valores padrão do `compose.yaml` são descartáveis e servem apenas
para desenvolvimento local.

Para construir a imagem sozinha:

```bash
docker build -t web-chat .
```

---

## Backend

Com o banco no ar:

```bash
./mvnw spring-boot:run
```

No Windows:

```bash
mvnw.cmd spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

Para ativar o perfil de desenvolvimento (log de SQL e de web):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Endpoints

Públicos:

```http
GET  /api/v1/health
POST /api/v1/auth/register
POST /api/v1/auth/login
```

Exigem `Authorization: Bearer <token>`:

```http
GET  /api/v1/users
GET  /api/v1/users/me
PUT  /api/v1/users/me
GET  /api/v1/users/{id}
GET  /api/v1/chats
POST /api/v1/chats
GET  /api/v1/chats/{chatId}
GET  /api/v1/chats/{chatId}/messages?before={id}&size={1-100}
POST /api/v1/chats/{chatId}/messages
PATCH /api/v1/chats/{chatId}/messages/read
```

Exigem token de um usuário com papel `ADMIN`:

```http
PUT   /api/v1/users/{id}
PATCH /api/v1/users/{id}/role
```

Detalhes em [docs/user.md](docs/user.md), [docs/auth.md](docs/auth.md) e
[docs/chat.md](docs/chat.md).

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "alexandre@email.com", "password": "123456" }
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

O `expiresIn` está em segundos.

### Papéis

Todo cadastro nasce `USER`. Um `ADMIN` pode atualizar qualquer colaborador e promover ou
rebaixar outros usuários, mas nunca alterar o próprio papel. Detalhes em
[docs/auth.md](docs/auth.md#autorização-por-papéis).

O primeiro administrador é criado na inicialização a partir de `ADMIN_EMAIL` e
`ADMIN_PASSWORD`. No perfil `dev` ele já existe: `admin@webchat.local` / `admin123`.

```bash
# promover o usuário 2 a ADMIN (com o token de um ADMIN)
curl -X PATCH http://localhost:8080/api/v1/users/2/role \
  -H "Authorization: Bearer <token-admin>" \
  -H "Content-Type: application/json" \
  -d '{"role":"ADMIN"}'
```

### Exemplo completo

```bash
# 1. cadastrar (público)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alexandre Oliveira","email":"alexandre@email.com","password":"123456"}'

# 2. autenticar
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alexandre@email.com","password":"123456"}'

# 3. usar o token
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <token>"

# 4. iniciar conversa com o usuário 2
#    201 quando cria; 200 com a mesma conversa se ela já existir
curl -X POST http://localhost:8080/api/v1/chats \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"participantId":2}'

# 5. listar minhas conversas (mais recentes primeiro, com lastMessage e unreadCount)
curl http://localhost:8080/api/v1/chats \
  -H "Authorization: Bearer <token>"

# 6. enviar mensagem na conversa 1
curl -X POST http://localhost:8080/api/v1/chats/1/messages \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"content":"oi, tudo bem?"}'

# 7. histórico: 50 mais recentes; para as anteriores, repita com before=<nextBefore>
curl "http://localhost:8080/api/v1/chats/1/messages?size=50" \
  -H "Authorization: Bearer <token>"

# 8. marcar como lidas as mensagens recebidas
curl -X PATCH http://localhost:8080/api/v1/chats/1/messages/read \
  -H "Authorization: Bearer <token>"
```

### Tempo real (WebSocket)

STOMP sobre WebSocket em `ws://localhost:8080/ws`, autenticado com o JWT no frame `CONNECT`:

| Frame | Destino | Conteúdo |
| ----- | ------- | -------- |
| `SEND` | `/app/chats/{chatId}/messages` | `{ "content": "..." }` |
| `SUBSCRIBE` | `/user/queue/messages` | mensagens novas das suas conversas |
| `SUBSCRIBE` | `/user/queue/read` | avisos de leitura (✓✓) |
| `SUBSCRIBE` | `/user/queue/errors` | erros do seu último envio |
| `SUBSCRIBE` | `/topic/presence` | quem ficou online/offline |

Mensagens enviadas pelo `POST` REST também são entregues em tempo real. Protocolo completo
em [docs/chat.md](docs/chat.md#websocket).

### Interface web

`http://localhost:8080` usa a API e o WebSocket: abrir um contato inicia ou reaproveita a
conversa e carrega o histórico; mensagens chegam na hora, as não lidas aparecem na barra
lateral, os ✓✓ atualizam quando o outro lê e o anel verde no avatar indica quem está online.

Para testar a conversa, abra duas janelas (uma delas anônima, já que o token fica no
`localStorage`) e entre com usuários diferentes.

---

## Configuração

A conexão com o banco é lida de variáveis de ambiente, com valores padrão iguais
aos do `compose.yaml` para facilitar o desenvolvimento local:

| Variável      | Padrão      |
| ------------- | ----------- |
| `DB_HOST`     | `localhost` |
| `DB_PORT`     | `5432`      |
| `DB_NAME`     | `webchat`   |
| `DB_USER`     | `webchat`   |
| `DB_PASSWORD` | `webchat`   |
| `SERVER_PORT` | `8080`      |
| `JWT_EXPIRATION` | `3600` (segundos) |
| `JWT_SECRET` | **sem padrão** — obrigatória |
| `ADMIN_EMAIL` | vazio — nenhum administrador inicial |
| `ADMIN_PASSWORD` | vazio — obrigatória se o admin ainda não existir |
| `ADMIN_NAME` | `Administrador` |
| `WS_ALLOWED_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` — origens aceitas no WebSocket |

`JWT_SECRET` não tem valor padrão de propósito: sem ela a aplicação não inicia, para
que nenhum segredo fique embutido no código. Precisa de no mínimo 32 bytes (HS256).

```powershell
# PowerShell
$env:JWT_SECRET = "troque-por-um-valor-longo-e-aleatorio-de-32-bytes"
.\mvnw.cmd spring-boot:run
```

```bash
# bash
JWT_SECRET="troque-por-um-valor-longo-e-aleatorio-de-32-bytes" ./mvnw spring-boot:run
```

Alternativa para desenvolvimento: o perfil `dev` já traz valores descartáveis de
`JWT_SECRET` e do administrador inicial, então
`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` funciona sem nenhuma
variável. Esses valores não servem para nenhum outro ambiente.

Esses padrões valem apenas para desenvolvimento local. Em qualquer outro ambiente
as variáveis devem ser fornecidas externamente — nenhuma credencial real deve ser
versionada.

---

## Migrations

O schema é versionado com Flyway, em `src/main/resources/db/migration`.
As migrations são aplicadas automaticamente na inicialização da aplicação.
O Hibernate roda em `ddl-auto: validate` e nunca altera o banco.

---

## Testes

```bash
./mvnw test
```

Os testes usam um banco H2 em memória (perfil `test`), portanto não exigem Docker.

São 161 testes: unitários dos services, testes de controller (`@WebMvcTest`), de repository
(`@DataJpaTest`) e de integração com a cadeia de segurança real — incluindo
`RealtimeIntegrationTest`, que sobe o servidor e conversa por WebSocket, e
`MvpFlowIntegrationTest`, que percorre o fluxo completo do MVP: cadastro → login → criação da
conversa → envio → histórico → leitura.

---

## Estrutura

```text
br.edu.webchat
├── WebChatApplication.java
├── auth/     config, controller, dto, filter, jwt, service
├── user/     config, controller, dto, entity, repository, service
├── chat/     controller, dto, entity, repository, service, websocket
└── shared/
    ├── config/        PasswordEncoder (BCrypt)
    ├── controller/    health check
    └── exception/     tratamento global de erros
```
