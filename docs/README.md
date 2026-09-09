# Documentação dos módulos

Documentação de referência do backend do Web Chat, organizada por módulo.

A fonte de verdade do projeto é a [WEB-CHAT-SPEC.md](../WEB-CHAT-SPEC.md). Estes
documentos detalham cada módulo e registram decisões tomadas durante a
implementação; em caso de divergência, a spec prevalece.

## Módulos

| Módulo | Responsabilidade | Fase | Situação |
| ------ | ---------------- | ---- | -------- |
| [shared](shared.md) | Configuração, tratamento de erros, health check | 1 | Implementado |
| [user](user.md) | Colaboradores: cadastro, consulta, perfil, status | 2 | Implementado |
| [auth](auth.md) | Registro, login, JWT, Spring Security | 3 | Não iniciado |
| [chat](chat.md) | Conversas, mensagens, histórico, WebSocket | 4–6 | Não iniciado |

## Convenções comuns

As regras abaixo valem para todos os módulos e não são repetidas em cada documento.

### Camadas

Cada módulo segue MVC, com o fluxo:

```text
Controller -> Service -> Repository -> Database
```

- **Controller**: recebe a requisição, valida a entrada e devolve a resposta HTTP.
  Não contém regra de negócio.
- **Service**: regras de negócio, validações de negócio e transações.
- **Repository**: acesso a dados via Spring Data JPA.
- **Entity**: representação persistida. **Nunca é exposta diretamente pela API.**
- **DTO**: contrato de entrada e saída da API.

### API

- Prefixo: `/api/v1`
- Recursos no plural: `/users`, `/chats`, `/messages`
- JSON em `camelCase`
- IDs numéricos: `Long` no Java, `BIGINT` no PostgreSQL, gerados pelo banco
- Datas em `Instant` / `OffsetDateTime` (ISO-8601, UTC), nunca `java.util.Date`

### Validação

Entrada validada com Bean Validation (`@Valid` nos controllers). Regras de negócio
permanecem nos services. Erros de validação são traduzidos automaticamente pelo
tratamento global — ver [shared](shared.md#erros-de-validação).

### Erros

Todas as respostas de erro usam o formato único `ApiError`, descrito em
[shared](shared.md#formato-de-erro).

### Banco

O schema é versionado exclusivamente por migrations Flyway em
`src/main/resources/db/migration`. O Hibernate roda em `ddl-auto: validate` e nunca
altera o banco. Cada módulo cria as próprias migrations na sua fase.
