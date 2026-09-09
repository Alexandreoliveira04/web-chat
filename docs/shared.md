# Módulo `shared`

**Pacote:** `br.edu.webchat.shared`
**Fase:** 1 — Fundação
**Situação:** implementado

Componentes de infraestrutura usados por todos os módulos. Deve conter apenas o que
é realmente compartilhado — não é depósito de classes sem responsabilidade clara.

## Estrutura

```text
shared/
├── config/       vazio nesta fase
├── controller/
│   └── HealthController
└── exception/
    ├── ApiError
    ├── ConflictException
    ├── GlobalExceptionHandler
    └── NotFoundException
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
  "message": "Chat nao encontrado",
  "path": "/api/v1/chats/9f1c..."
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
| Entrada inválida | `MethodArgumentNotValidException` | 400 |
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
rejeitado:

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

Apenas duas, sem hierarquia intermediária. Os services as lançam diretamente:

```java
throw new NotFoundException("Usuario nao encontrado");
throw new ConflictException("E-mail ja cadastrado");
```

Novas exceções só devem ser criadas quando representarem um status HTTP que ainda
não é coberto.

## `config/`

Vazio nesta fase — nenhuma configuração compartilhada é necessária ainda. Destino
previsto: `SecurityConfig` (fase 3) e a configuração de WebSocket (fase 6), caso não
fiquem dentro dos respectivos módulos.

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

`JWT_SECRET` será acrescentada na fase 3, sem valor padrão.

### Perfis

| Perfil | Arquivo | Uso |
| ------ | ------- | --- |
| (nenhum) | `application.yml` | Execução local contra o PostgreSQL do Docker |
| `dev` | `application-dev.yml` | Acrescenta log de SQL e de web |
| `test` | `src/test/resources/application-test.yml` | H2 em memória, Flyway desabilitado |

O perfil `test` existe para que `mvnw test` rode sem exigir Docker. A contrapartida é
que os testes não validam SQL específico do PostgreSQL — quando as migrations reais
existirem, vale avaliar Testcontainers.
