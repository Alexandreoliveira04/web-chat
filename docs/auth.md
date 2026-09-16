# Módulo `auth`

**Pacote:** `br.edu.webchat.auth`
**Fase:** 3 (+ 3.1 — papéis e permissões)
**Situação:** implementado

Responsável pela autenticação (registro, login, emissão e validação de JWT) e pela
autorização: a configuração do Spring Security que protege o restante da API e aplica
as regras por papel.

O AUTH **autentica**; quem responde pelos dados do colaborador continua sendo o
módulo [user](user.md).

## Estrutura

```text
auth/
├── config/       SecurityConfig, SecurityErrorHandler
├── controller/   AuthController
├── dto/          LoginRequest, LoginResponse
├── filter/       JwtAuthenticationFilter
├── jwt/          JwtService
└── service/      AuthService, CustomUserDetailsService
```

Não há `entity/` nem `repository/`: as credenciais e o papel ficam na tabela `users` e
são lidos pelo `UserRepository` do módulo USER. O JWT não é persistido.

## Registro

```http
POST /api/v1/auth/register
Content-Type: application/json

{ "name": "Alexandre Oliveira", "email": "alexandre@email.com", "password": "123456" }
```

Resposta `201 Created`, com `Location: /api/v1/users/{id}` e o `UserResponse` no corpo.

O `AuthController` delega ao `UserService.create` do módulo USER, que continua dono das
regras de cadastro (e-mail normalizado, 409 para duplicado, senha em BCrypt).

O usuário criado é **sempre `USER`**. `CreateUserRequest` não tem campo `role`, então
um `"role": "ADMIN"` no JSON é simplesmente ignorado.

> Até o Módulo 0 o cadastro ficava em `POST /api/v1/users`. A rota foi removida e hoje
> responde 401 sem token, como qualquer rota protegida.

## Login

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

Fluxo:

```text
LoginRequest
   ↓ normaliza o e-mail (trim + minúsculas)
UserRepository.findByEmail
   ↓ PasswordEncoder.matches (BCrypt)
JwtService.generateToken
   ↓
LoginResponse
```

E-mail inexistente e senha incorreta produzem **exatamente a mesma resposta 401**,
com a mensagem `E-mail ou senha invalidos`. Distinguir os dois casos revelaria quais
e-mails estão cadastrados.

O login **não** altera o `status` do usuário: ele permanece `OFFLINE`. A presença
real será controlada pelo WebSocket (fase 6).

## Requisições autenticadas

```http
GET /api/v1/users/me
Authorization: Bearer <token>
```

O `JwtAuthenticationFilter` lê o cabeçalho, valida o token e popula o
`SecurityContext`. Token ausente, malformado, adulterado ou expirado **não** gera
erro no filtro: a requisição segue sem autenticação e o Spring Security responde 401
se o endpoint for protegido.

O filtro não é um bean. Ele é construído pelo `SecurityConfig` — registrado como
bean, o Spring Boot também o adicionaria à cadeia de filtros do servlet, executando-o
duas vezes por requisição.

## Endpoints

| Rota | Acesso |
| ---- | ------ |
| `GET /api/v1/health` | público |
| `POST /api/v1/auth/register` | público |
| `POST /api/v1/auth/login` | público |
| frontend (`/`, `/login`, `/chat`, `/_next/**`, assets) | público |
| `GET /api/v1/users` | autenticado |
| `GET /api/v1/users/{id}` | autenticado |
| `GET /api/v1/users/me` | autenticado |
| `PUT /api/v1/users/me` | autenticado |
| `PUT /api/v1/users/{id}` | **ADMIN** |
| `PATCH /api/v1/users/{id}/role` | **ADMIN** |
| `/api/v1/chats/**` | autenticado (e participante da conversa, checado no service) |
| `/ws` (handshake WebSocket) | público no HTTP; autenticação no frame STOMP `CONNECT` (ver abaixo) |
| qualquer outra | autenticado |

**A ordem das regras importa.** O Spring Security usa a primeira regra que casar, e o
padrão `/api/v1/users/{id}` também casa com `/api/v1/users/me`. Por isso a regra de
`PUT /users/me` vem antes da de `PUT /users/{id}` no `SecurityConfig`.

Como a regra final é `anyRequest().authenticated()`, uma rota inexistente sob
`/api/v1` passa a responder **401** em vez de 404 quando não há token — o Spring
Security decide antes de o roteamento acontecer. É o comportamento desejado: não
revela quais rotas existem.

## WebSocket

O handshake `GET /ws` é liberado no `SecurityConfig` porque a API de WebSocket dos
navegadores não permite enviar o header `Authorization` no upgrade. O JWT vai no frame STOMP
`CONNECT`:

```text
CONNECT
Authorization:Bearer <token>
```

O `StompAuthInterceptor` (módulo chat) reaproveita o `JwtService` e o `UserDetailsService`
deste módulo: token ausente, inválido, expirado ou de usuário removido gera frame `ERROR`
(`Autenticacao necessaria`) e a conexão é encerrada. Com o token válido, o usuário vira o
`Principal` da sessão STOMP, usado em todos os frames seguintes — o remetente de uma mensagem
nunca vem do payload.

Proteções adicionais:

- **origem do handshake** restrita a `WS_ALLOWED_ORIGINS` (padrão
  `http://localhost:*,http://127.0.0.1:*`); outra origem recebe HTTP 403;
- `SUBSCRIBE` e `SEND` são recusados antes do `CONNECT` autenticado e fora dos destinos
  permitidos.

Como o token não vai em cookie, a conexão não carrega credenciais "automáticas" do navegador;
por isso não se usa o CSRF do `spring-security-messaging`.

Detalhes do protocolo em [chat](chat.md#websocket).

## Autorização por papéis

| Papel | Pode |
| ----- | ---- |
| `USER` | ler colaboradores; ler e atualizar o próprio perfil (`/users/me`); usar o chat |
| `ADMIN` | tudo de `USER`; atualizar qualquer colaborador (`PUT /users/{id}`); alterar papéis (`PATCH /users/{id}/role`) |

Como funciona:

1. O papel fica em `users.role` (enum `Role` do módulo USER).
2. O `CustomUserDetailsService` carrega o usuário e registra o papel com
   `.roles(user.getRole().name())`, o que gera a authority `ROLE_USER` ou `ROLE_ADMIN`.
3. O `SecurityConfig` exige `hasRole("ADMIN")` nas rotas administrativas.
4. Um `USER` que chama uma rota de `ADMIN` recebe **403** do `SecurityErrorHandler`,
   antes de o controller ser executado.

**O papel não vai no JWT.** O filtro recarrega o usuário do banco a cada requisição, então
promover ou rebaixar alguém vale **na próxima requisição**, sem esperar o token expirar.
O custo é uma consulta por requisição, a mesma que já era feita para validar se o usuário
ainda existe.

**Ninguém altera o próprio papel.** Essa regra de negócio fica no `UserService.changeRole`
e lança `ForbiddenException` (403). Assim um administrador nunca se rebaixa por engano, e
como só um ADMIN rebaixa outro ADMIN, o sistema sempre mantém pelo menos um.

A autorização por **recurso** (por exemplo, "só participantes leem a conversa") não é
papel: ela fica nos services dos módulos e também usa `ForbiddenException`. O papel `ADMIN`
**não** dá acesso a conversas de terceiros.

### Administrador inicial

Na inicialização, o `AdminInitializer` (módulo USER) lê:

| Propriedade | Variável | Padrão |
| ----------- | -------- | ------ |
| `app.admin.email` | `ADMIN_EMAIL` | vazio: nenhum admin é criado |
| `app.admin.password` | `ADMIN_PASSWORD` | vazio |
| `app.admin.name` | `ADMIN_NAME` | `Administrador` |

- Se o e-mail já existe, o usuário é **promovido** a ADMIN, sem trocar nome nem senha.
- Se não existe, é criado como ADMIN. Nesse caso a senha é obrigatória (mínimo de 6
  caracteres) e a aplicação não sobe sem ela.
- A operação é idempotente: reiniciar a aplicação não duplica nada.

O perfil `dev` traz um administrador descartável (`admin@webchat.local` / `admin123`),
com o mesmo critério do `JWT_SECRET` de desenvolvimento: não serve para nenhum outro
ambiente.

## JWT

| Item | Valor |
| ---- | ----- |
| Algoritmo | HS256 (HMAC-SHA256) |
| Biblioteca | JJWT 0.12.6 |
| Claims | `sub` (e-mail), `iat`, `exp` — nada além disso |
| Validade | `jwt.expiration`, padrão 3600 s |
| Armazenamento | nenhum: o token é stateless e não vai ao banco |

`JwtService.extractEmail` devolve `Optional.empty()` para token inválido, adulterado
ou expirado, em vez de lançar exceção — é o filtro que chama, e ele apenas segue sem
autenticar. O token nunca é registrado em log; apenas o tipo da exceção, em `debug`.

O construtor recusa segredo com menos de 32 bytes, o mínimo exigido pelo HS256.

## Configuração

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600}
```

`JWT_SECRET` **não tem valor padrão**: sem a variável, a aplicação não sobe. Isso é
proposital — um segredo embutido no código acabaria em produção.

Para desenvolvimento local, o perfil `dev` define um valor claramente identificado
como descartável (`application-dev.yml`). Ele não é um segredo de produção.

## Erros

As falhas de segurança acontecem na cadeia de filtros, **antes** do
`DispatcherServlet`, então o `@RestControllerAdvice` não as alcança. O
`SecurityErrorHandler` implementa `AuthenticationEntryPoint` e `AccessDeniedHandler`
e escreve o mesmo [`ApiError`](shared.md#formato-de-erro) usado no resto da API.

| Situação | Status | Origem |
| -------- | ------ | ------ |
| Credenciais inválidas no login | 401 | `UnauthorizedException` → `GlobalExceptionHandler` |
| Token ausente, inválido ou expirado | 401 | `SecurityErrorHandler.commence` |
| Papel insuficiente para a rota | 403 (`Acesso negado`) | `SecurityErrorHandler.handle` |
| Regra de negócio proíbe a operação (ex.: alterar o próprio papel) | 403 | `ForbiddenException` → `GlobalExceptionHandler` |
| Entrada inválida | 400 | `GlobalExceptionHandler` |

## Testes

| Classe | Cobre |
| ------ | ----- |
| `JwtServiceTest` | geração, expiração, assinatura de outro segredo, payload adulterado, segredo curto |
| `AuthServiceTest` | login válido, e-mail inexistente, senha incorreta, mensagem idêntica nos dois casos |
| `AuthControllerTest` | registro: 201 + `Location`, 400 com campos, 409 |
| `SecurityIntegrationTest` | cadeia real: público × protegido, `/users/me`, token expirado/inválido, usuário removido; papéis: USER recebe 403 nas rotas de ADMIN, ADMIN atualiza qualquer um, ninguém altera o próprio papel, promoção vale com o mesmo token, `role` no corpo do registro é ignorado |

Os testes de controller com `@WebMvcTest` usam `@AutoConfigureMockMvc(addFilters =
false)`: eles verificam o comportamento do controller, e a segurança é coberta pelo
`SecurityIntegrationTest` com a cadeia completa.

## Pendências

- **Sem refresh token.** Expirado o JWT, é preciso novo login.
- **WebSocket valida o token só no `CONNECT`.** Uma conexão aberta continua ativa depois que
  o token expira; a próxima reconexão é recusada e o front volta ao login.
- **Um papel por usuário.** Não há permissões granulares nem múltiplos papéis; basta
  para o MVP. Se necessário, `role` pode virar uma tabela `user_roles`.
- **Nenhum estado de usuário bloqueia o login.** `UserStatus` só tem `ONLINE` e
  `OFFLINE`, e todo usuário nasce `OFFLINE` — bloquear esse valor impediria qualquer
  login. Um estado do tipo "inativo" exigiria um novo valor no enum, fora do escopo
  desta etapa.
- **CORS liberado por origem configurável** (`CORS_ALLOWED_ORIGINS`), necessário porque em
  desenvolvimento o frontend roda em outra porta. Em produção, front e API ficam na mesma
  origem.
