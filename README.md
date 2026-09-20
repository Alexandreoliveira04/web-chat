# Web Chat

Aplicação web de chat interno entre colaboradores: cadastro e login com papéis, conversas
individuais e em grupo, mensagens com edição e exclusão, histórico, marcação de leitura por
participante e presença — tudo em tempo real por WebSocket.

Backend em **Java 17 + Spring Boot 3**, organizado como monólito modular seguindo MVC, com
PostgreSQL. Frontend em **Next.js 16 + TypeScript**, publicado como export estático e servido
pelo próprio backend — um processo, um artefato.

```text
web-chat/
├── backend/     API REST + WebSocket (Maven)
├── frontend/    interface em Next.js
├── docs/        documentação técnica
├── compose.yaml PostgreSQL (e a aplicação, no profile "app")
└── Dockerfile   build do front + backend em uma imagem só
```

---

## Requisitos

- Java 17
- Node.js 20+
- Docker e Docker Compose

O Maven não precisa estar instalado: o wrapper (`mvnw`) acompanha o projeto em `backend/`.

---

## Rodando localmente

### 1. Banco

```bash
docker compose up -d
```

Sobe um PostgreSQL 16 em `localhost:5432`, com banco, usuário e senha `webchat`. Para parar,
`docker compose down`.

### 2. Backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # Windows: mvnw.cmd
```

A API sobe em `http://localhost:8080`. O perfil `dev` traz valores descartáveis de
`JWT_SECRET` e um administrador inicial (`admin@webchat.local` / `admin123`), então nenhuma
variável de ambiente é necessária para desenvolver. Fora do `dev`, `JWT_SECRET` é
obrigatória — ver [configuração](docs/backend/architecture.md#configuração).

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

A interface sobe em `http://localhost:3000`. Como o front e a API ficam em portas
diferentes em desenvolvimento, crie `frontend/.env.development` com:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
```

Para testar uma conversa, abra duas janelas (uma anônima, já que o token fica no
`localStorage`) e entre com usuários diferentes.

---

## Tudo em containers

Backend e frontend também rodam em container — a imagem constrói o front e empacota tudo em
um jar só:

```bash
docker compose --profile app up -d --build     # http://localhost:8080
docker compose --profile app logs -f app
docker compose --profile app down
```

Os valores de `JWT_SECRET`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` no `compose.yaml` são
descartáveis e servem apenas para desenvolvimento local; em qualquer outro ambiente,
informe-os por variável de ambiente.

---

## Testes

```bash
cd backend
./mvnw test
```

Rodam em H2 na memória (perfil `test`), sem exigir Docker.

---

## Documentação

A documentação técnica fica em [docs/](docs/README.md):

| | |
| --- | --- |
| [Arquitetura do backend](docs/backend/architecture.md) | stack, módulos, camadas, convenções, configuração e testes |
| [Módulos do backend](docs/backend/modules/) | [shared](docs/backend/modules/shared.md), [user](docs/backend/modules/user.md), [auth](docs/backend/modules/auth.md), [chat](docs/backend/modules/chat.md) — inclui a referência da API REST e do WebSocket |
| [Arquitetura do frontend](docs/frontend/architecture.md) | stack, comunicação com a API, tempo real e build |
| [Módulos do frontend](docs/frontend/modules/) | [páginas](docs/frontend/modules/pages.md), [componentes](docs/frontend/modules/components.md), [lib](docs/frontend/modules/libs.md) |
