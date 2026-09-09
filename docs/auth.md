# Módulo `auth`

**Pacote:** `br.edu.webchat.auth`
**Fase:** 3
**Situação:** implementado

Responsável pela autenticação: login, emissão e validação de JWT, e a configuração do
Spring Security que protege o restante da API.

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

Não há `entity/` nem `repository/`: as credenciais ficam na tabela `users` e são
lidas pelo `UserRepository` do módulo USER. O AUTH **não** alterou o schema — nenhuma
migration foi criada nesta etapa, e o JWT não é persistido.

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
| `POST /api/v1/auth/login` | público |
| `POST /api/v1/users` | público (cadastro inicial) |
| `GET /api/v1/users` | autenticado |
| `GET /api/v1/users/{id}` | autenticado |
| `GET /api/v1/users/me` | autenticado |
| `PUT /api/v1/users/{id}` | autenticado |
| `/api/v1/chats/**`, `/api/v1/messages/**` | autenticado (módulos ainda não implementados) |
| qualquer outra | autenticado |

Como a regra final é `anyRequest().authenticated()`, uma rota inexistente sob
`/api/v1` passa a responder **401** em vez de 404 quando não há token — o Spring
Security decide antes de o roteamento acontecer. É o comportamento desejado: não
revela quais rotas existem.

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
| Autenticado sem permissão | 403 | `SecurityErrorHandler.handle` |
| Entrada inválida | 400 | `GlobalExceptionHandler` |

## Testes

| Classe | Cobre |
| ------ | ----- |
| `JwtServiceTest` | geração, expiração, assinatura de outro segredo, payload adulterado, segredo curto |
| `AuthServiceTest` | login válido, e-mail inexistente, senha incorreta, mensagem idêntica nos dois casos |
| `SecurityIntegrationTest` | cadeia real: público × protegido, `/users/me`, token expirado/inválido, usuário removido |

Os testes de controller com `@WebMvcTest` usam `@AutoConfigureMockMvc(addFilters =
false)`: eles verificam o comportamento do controller, e a segurança é coberta pelo
`SecurityIntegrationTest` com a cadeia completa.

## Pendências

- **Sem refresh token.** Expirado o JWT, é preciso novo login.
- **Sem roles/RBAC.** A lista de authorities é vazia; o 403 está configurado, mas
  nenhuma regra o dispara hoje.
- **Nenhum estado de usuário bloqueia o login.** `UserStatus` só tem `ONLINE` e
  `OFFLINE`, e todo usuário nasce `OFFLINE` — bloquear esse valor impediria qualquer
  login. Um estado do tipo "inativo" exigiria um novo valor no enum, fora do escopo
  desta etapa.
- **CORS não configurado.** Será necessário quando o frontend Next.js existir.
