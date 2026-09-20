# Arquitetura — Backend

API REST + WebSocket do Web Chat, em Java 17 e Spring Boot 3.5. Um único projeto Maven,
organizado como **monólito modular**: um processo, um artefato de deploy, quatro módulos com
fronteiras claras dentro do mesmo jar.

A documentação de cada módulo fica em [modules/](modules/).

## Stack

| Item | Escolha |
| ---- | ------- |
| Linguagem | Java 17 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation, Security, WebSocket) |
| Persistência | PostgreSQL 16 + Hibernate |
| Migrations | Flyway |
| Autenticação | JWT (JJWT 0.12.6), stateless |
| Tempo real | STOMP sobre WebSocket nativo, broker em memória do Spring |
| Build | Maven (wrapper `mvnw` incluso) |
| Testes | JUnit 5, Mockito, Spring Security Test, H2 em memória |

Nada de Redis, fila externa ou microsserviço: o escopo do projeto não justifica a
complexidade, e o broker em memória do Spring atende o tempo real de um processo só.

## Visão geral

```text
          Next.js (export estático)
                   │
        REST /api/v1  +  STOMP /ws
                   │
        ┌──────────▼──────────┐
        │     Spring Boot     │
        │  ┌───────────────┐  │
        │  │ auth   user   │  │
        │  │ chat   shared │  │
        │  └───────────────┘  │
        └──────────┬──────────┘
                   │ JPA / Flyway
              PostgreSQL
```

O frontend não é um processo separado em produção: o build do Next gera HTML/JS estáticos
que o próprio Spring Boot serve, então sobe um container só. Ver
[frontend/arquitetura](../frontend/architecture.md).

## Módulos

| Módulo | Responsabilidade |
| ------ | ---------------- |
| [shared](modules/shared.md) | Configuração comum, tratamento global de erros, health check |
| [user](modules/user.md) | Colaboradores: cadastro, consulta, perfil, status, papel |
| [auth](modules/auth.md) | Registro, login, JWT, Spring Security, autorização por papéis |
| [chat](modules/chat.md) | Conversas, grupos, mensagens, histórico, leitura, WebSocket |

Dependências permitidas entre eles:

```text
auth ──> user ──┐
chat ──> user ──┼──> shared
chat ──> auth ──┘
```

`chat` depende de `auth` em um ponto só: o `StompAuthInterceptor` reaproveita o `JwtService`
e o `UserDetailsService` para autenticar o frame `CONNECT`.

`shared` não conhece ninguém — é o único módulo do qual todos podem depender, e por isso
guarda apenas o que é de fato comum. Regra prática: se uma classe serve a um módulo só, ela
mora nesse módulo, mesmo que pareça "infraestrutura". Foi o que manteve o `SecurityConfig`
em `auth` e o `WebSocketConfig` em `chat`.

## Camadas

Cada módulo segue MVC:

```text
Controller ──> Service ──> Repository ──> Database
```

- **Controller**: recebe a requisição, valida a entrada (`@Valid`) e devolve a resposta
  HTTP. Não contém regra de negócio.
- **Service**: regras de negócio, validações que dependem de estado e transações.
- **Repository**: acesso a dados via Spring Data JPA.
- **Entity**: representação persistida. **Nunca é exposta diretamente pela API.**
- **DTO**: contrato de entrada e saída — `records` imutáveis.

A separação entidade × DTO é o que permite mudar o schema sem quebrar o contrato da API, e
impede que um campo como `password` vaze por descuido em uma serialização.

## Convenções

Valem para todos os módulos e não se repetem em cada documento.

### API

- Prefixo `/api/v1`, recursos no plural: `/users`, `/chats`, `/messages`
- JSON em `camelCase`
- IDs numéricos: `Long` no Java, `BIGINT` no PostgreSQL, gerados pelo banco
- Datas em `Instant` / `OffsetDateTime` (ISO-8601, UTC), nunca `java.util.Date`
- `201 Created` com `Location` em criação; `200` quando a operação reaproveita algo existente

### Autenticação

Salvo o que está listado como público em [auth](modules/auth.md#endpoints), todo endpoint
exige `Authorization: Bearer <token>`. Algumas rotas exigem o papel `ADMIN` — ver
[autorização por papéis](modules/auth.md#autorização-por-papéis).

Autorização por **recurso** ("só participante lê a conversa") não é papel: fica no service do
módulo e lança `ForbiddenException`. `ADMIN` não dá acesso a conversa de terceiro.

### Erros

Toda resposta de erro usa o mesmo `ApiError`, descrito em
[shared](modules/shared.md#formato-de-erro). Erros de Bean Validation viram `400` com o mapa
`fields`; as exceções de negócio são uma por status HTTP, sem hierarquia intermediária.

### Banco

O schema é versionado **exclusivamente** por migrations Flyway em
`backend/src/main/resources/db/migration`, aplicadas na inicialização. O Hibernate roda em
`ddl-auto: validate` e nunca altera o banco — se a entidade e o schema divergirem, a
aplicação não sobe, em vez de corrigir sozinha e mascarar o erro.

Cada módulo cria as próprias migrations. São 10 hoje, de `V1__init.sql` a
`V10__add_edit_and_delete_to_messages.sql`.

## Divisão REST × WebSocket

| REST | WebSocket |
| ---- | --------- |
| Criar e listar conversas, consultar histórico, perfil | Envio e recebimento de mensagens, recibo de leitura, presença |

A persistência é sempre do service: os dois caminhos passam pelo mesmo `MessageService`, para
que não existam duas escritas divergentes. Enviar por `POST` também notifica em tempo real.
Protocolo completo em [chat](modules/chat.md#websocket).

## Configuração

Tudo vem de variável de ambiente, com padrões iguais aos do `compose.yaml` — que servem
**apenas** para desenvolvimento local. A tabela completa está em
[shared](modules/shared.md#configuração-da-aplicação).

`JWT_SECRET` não tem padrão de propósito: sem ela a aplicação não sobe. Um segredo embutido
no código acabaria em produção. Mínimo de 32 bytes (HS256).

Perfis: nenhum (local contra o Postgres do Docker), `dev` (log de SQL, `JWT_SECRET` e
administrador descartáveis) e `test` (H2 em memória, sem Docker).

## Testes

```bash
cd backend
./mvnw test
```

São 179 métodos `@Test`, em quatro camadas:

| Tipo | Como | Cobre |
| ---- | ---- | ----- |
| Unitário | JUnit + Mockito | regras de negócio dos services |
| Controller | `@WebMvcTest` | status HTTP, validação, formato da resposta |
| Repository | `@DataJpaTest` (H2) | queries, constraints, geração de id |
| Integração | `@SpringBootTest` | cadeia de segurança real, fluxo completo, WebSocket com cliente STOMP de verdade |

`RealtimeIntegrationTest` sobe o servidor em porta aleatória e conversa por WebSocket;
`MvpFlowIntegrationTest` percorre cadastro → login → conversa → envio → histórico → leitura.

O perfil `test` usa H2 para que a suíte rode sem Docker — a contrapartida está registrada em
[shared](modules/shared.md#perfis).

## Limitações conhecidas

- **Sem refresh token**: expirado o JWT, é preciso novo login.
- **Broker em memória**: o tempo real funciona em um processo. Rodar duas instâncias exigiria
  um broker externo (RabbitMQ/Redis) ou sessões afinadas por usuário.
- **Sem paginação em `GET /users`**: adequado ao número de colaboradores esperado.
- **Sem remoção de usuário**: fora do escopo.
- **Deploy em cloud não feito**: o projeto roda local e em container, mas não há pipeline nem
  ambiente publicado.
