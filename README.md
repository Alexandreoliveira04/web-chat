# Web Chat

Aplicação web de chat interno entre colaboradores.

Backend em Java 17 + Spring Boot 3, organizado como monólito modular seguindo MVC.
A especificação completa do projeto está em [WEB-CHAT-SPEC.md](WEB-CHAT-SPEC.md) e a
documentação por módulo em [docs/](docs/README.md).

> Estado atual: **Fase 3 — AUTH**. O módulo `chat` ainda não foi implementado.

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
POST /api/v1/auth/login
POST /api/v1/users
```

Exigem `Authorization: Bearer <token>`:

```http
GET  /api/v1/users
GET  /api/v1/users/me
GET  /api/v1/users/{id}
PUT  /api/v1/users/{id}
```

Detalhes em [docs/user.md](docs/user.md) e [docs/auth.md](docs/auth.md).

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

### Exemplo completo

```bash
# 1. cadastrar (público)
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alexandre Oliveira","email":"alexandre@email.com","password":"123456"}'

# 2. autenticar
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alexandre@email.com","password":"123456"}'

# 3. usar o token
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <token>"
```

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

Alternativa para desenvolvimento: o perfil `dev` já traz um valor descartável, então
`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` funciona sem a
variável. Esse valor não serve para nenhum outro ambiente.

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

---

## Estrutura

```text
br.edu.webchat
├── WebChatApplication.java
├── auth/     config, controller, dto, filter, jwt, service
├── user/     controller, dto, entity, repository, service
├── chat/     (Fases 4-6)
└── shared/
    ├── config/        PasswordEncoder (BCrypt)
    ├── controller/    health check
    └── exception/     tratamento global de erros
```
