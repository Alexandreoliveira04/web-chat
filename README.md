# Web Chat

Aplicação web de chat interno entre colaboradores.

Backend em Java 17 + Spring Boot 3, organizado como monólito modular seguindo MVC.
A especificação completa do projeto está em [WEB-CHAT-SPEC.md](WEB-CHAT-SPEC.md) e a
documentação por módulo em [docs/](docs/README.md).

> Estado atual: **Fase 1 — Fundação**. Os módulos `auth`, `user` e `chat` ainda não
> foram implementados.

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

### Health check

```bash
curl http://localhost:8080/api/v1/health
```

```json
{ "status": "UP" }
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
├── auth/     (Fase 3)
├── user/     (Fase 2)
├── chat/     (Fases 4-6)
└── shared/
    ├── config/
    ├── controller/   health check
    └── exception/    tratamento global de erros
```
