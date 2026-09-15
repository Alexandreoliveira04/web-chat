# Módulo `shared`

**Pacote:** `br.edu.webchat.shared`
**Fase:** 1 — Fundação
**Situação:** implementado

Componentes de infraestrutura usados por todos os módulos. Deve conter apenas o que
é realmente compartilhado — não é depósito de classes sem responsabilidade clara.

## Estrutura

```text
shared/
├── config/
│   └── PasswordEncoderConfig
├── controller/
│   └── HealthController
└── exception/
    ├── ApiError
    ├── BadRequestException
    ├── ConflictException
    ├── ForbiddenException
    ├── GlobalExceptionHandler
    ├── NotFoundException
    └── UnauthorizedException
```

## Health check

```http
GET /api/v1/health
```

```json
{ "status": "UP" }
```

Endpoint deliberadamente simples, sem Actuator. Serve para confirmar que a API
está no ar; não verifica banco nem dependências externas.

## Formato de erro

Toda resposta de erro da API usa a mesma estrutura, definida em `ApiError`:

```json
{
  "timestamp": "2026-09-09T02:04:36.733641600Z",
  "status": 404,
  "error": "Not Found",
  "message": "Conversa nao encontrada: 99",
  "path": "/api/v1/chats/99"
}
```

| Campo | Descrição |
| ----- | --------- |
| `timestamp` | Instante do erro, em UTC |
| `status` | Código HTTP |
| `error` | Reason phrase do status |
| `message` | Mensagem legível |
| `path` | URI da requisição |
| `fields` | Só presente em erros de validação |

## Tratamento global

`GlobalExceptionHandler` é um `@RestControllerAdvice` que estende
`ResponseEntityExceptionHandler`.

| Situação | Exceção | Status |
| -------- | ------- | ------ |
| Corpo inválido (`@Valid @RequestBody`) | `MethodArgumentNotValidException` | 400 |
| Parâmetro inválido (`@RequestParam` com `@Min`, `@Max`, `@Positive`...) | `HandlerMethodValidationException` | 400 |
| Regra de negócio rejeita a entrada | `BadRequestException` | 400 |
| Credenciais inválidas | `UnauthorizedException` | 401 |
| Operação proibida para o usuário autenticado | `ForbiddenException` | 403 |
| Recurso inexistente | `NotFoundException` | 404 |
| Conflito de estado | `ConflictException` | 409 |
| Erros próprios do Spring MVC (rota inexistente, método não suportado, corpo malformado, ...) | tratadas por `ResponseEntityExceptionHandler` | status original |
| Qualquer outra | `Exception` | 500 |

> **Por que estender `ResponseEntityExceptionHandler`:** sem isso, o handler
> genérico de `Exception` captura também as exceções do próprio Spring MVC e as
> transforma em 500. Uma rota inexistente retornava 500 em vez de 404. A classe base
> trata essas exceções com o status correto, e `handleExceptionInternal` foi
> sobrescrito para converter a resposta ao formato `ApiError`.

O handler de `Exception` é a rede de segurança: registra o stack trace no log e
devolve uma mensagem genérica, para não vazar detalhe interno ao cliente.

### Erros de validação

Erros de Bean Validation retornam 400 com o mapa `fields`, um par por campo
rejeitado. Vale tanto para o corpo da requisição quanto para parâmetros de query
validados no controller — nesse caso a chave é o nome do parâmetro (ex.:
`GET /chats/1/messages?size=0` → `"fields": { "size": "deve ser maior que ou igual à 1" }`).
Os dois handlers (`handleMethodArgumentNotValid` e `handleHandlerMethodValidationException`)
são sobrescritos para produzir o mesmo formato:

```json
{
  "timestamp": "2026-09-09T02:04:36.733641600Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados invalidos",
  "path": "/api/v1/auth/register",
  "fields": {
    "email": "deve ser um endereço de e-mail bem formado",
    "password": "tamanho deve ser entre 8 e 100"
  }
}
```

### Exceções de negócio

Uma por status HTTP, sem hierarquia intermediária. Os services as lançam diretamente:

```java
throw new BadRequestException("Nao e possivel iniciar uma conversa consigo mesmo"); // 400
throw new UnauthorizedException("E-mail ou senha invalidos");       // 401
throw new ForbiddenException("Nao e permitido alterar o proprio papel"); // 403
throw new NotFoundException("Usuario nao encontrado");              // 404
throw new ConflictException("E-mail ja cadastrado");                // 409
```

`BadRequestException` cobre o 400 que o Bean Validation não consegue expressar, porque
depende de estado (ex.: `participantId` igual ao próprio usuário autenticado). Ela não
tem o mapa `fields`: a mensagem descreve a regra violada.

`ForbiddenException` é para regras de negócio avaliadas no service (ex.: alterar o próprio
papel, ou acessar uma conversa da qual não participa). Papel insuficiente para uma rota é
barrado antes, pelo Spring Security, e também devolve 403 no mesmo formato — ver
[auth](auth.md#erros).

Novas exceções só devem ser criadas quando representarem um status HTTP que ainda
não é coberto.

## `config/`

Contém apenas o `PasswordEncoderConfig` (BCrypt), usado pelos módulos USER e AUTH. O
`SecurityConfig` ficou no módulo AUTH, e a configuração de WebSocket ficará no módulo CHAT.

## Configuração da aplicação

Definida em `src/main/resources/application.yml`, com valores lidos de variáveis de
ambiente. Os padrões correspondem ao `compose.yaml` e servem **apenas** para
desenvolvimento local:

| Variável | Padrão |
| -------- | ------ |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `webchat` |
| `DB_USER` | `webchat` |
| `DB_PASSWORD` | `webchat` |
| `SERVER_PORT` | `8080` |
| `JWT_SECRET` | sem padrão — obrigatória |
| `JWT_EXPIRATION` | `3600` |
| `ADMIN_EMAIL` | vazio — nenhum admin inicial |
| `ADMIN_PASSWORD` | vazio |
| `ADMIN_NAME` | `Administrador` |
| `WS_ALLOWED_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` — origens aceitas no handshake WebSocket |

### Perfis

| Perfil | Arquivo | Uso |
| ------ | ------- | --- |
| (nenhum) | `application.yml` | Execução local contra o PostgreSQL do Docker |
| `dev` | `application-dev.yml` | Log de SQL e de web; `JWT_SECRET` e administrador descartáveis |
| (nenhum, em container) | `compose.yaml`, profile `app` | Backend no Docker; as variáveis vêm do `environment` do serviço |
| `test` | `src/test/resources/application-test.yml` | H2 em memória, Flyway desabilitado |

O perfil `test` existe para que `mvnw test` rode sem exigir Docker. A contrapartida é
que os testes não validam SQL específico do PostgreSQL — quando as migrations reais
existirem, vale avaliar Testcontainers.
