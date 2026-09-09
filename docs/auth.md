# Módulo `auth`

**Pacote:** `br.edu.webchat.auth`
**Fase:** 3
**Situação:** não iniciado — o pacote existe, sem classes

Responsável pela autenticação: registro, login, emissão e validação de JWT, e
integração com o Spring Security.

> Este documento descreve o contrato planejado a partir da
> [WEB-CHAT-SPEC.md](../WEB-CHAT-SPEC.md) e deve ser atualizado junto com o código
> na fase 3.

## Estrutura prevista

```text
auth/
├── controller/   AuthController
├── dto/          RegisterRequest, LoginRequest, LoginResponse
├── security/     filtro JWT, serviço de token, SecurityConfig
└── service/      AuthService
```

O módulo não tem `entity/` nem `repository/` próprios: as credenciais ficam na
tabela `users`, acessada pelo repositório do módulo [user](user.md).

## Dependências ainda não adicionadas

O `pom.xml` atual **não** inclui Spring Security nem biblioteca de JWT — elas entram
nesta fase. A escolha da biblioteca de JWT ainda não foi feita.

## API

| Método | Rota | Descrição | Autenticação |
| ------ | ---- | --------- | ------------ |
| `POST` | `/api/v1/auth/register` | Cadastra um colaborador | Não |
| `POST` | `/api/v1/auth/login` | Autentica e devolve o JWT | Não |

Estes são os únicos endpoints públicos. Todo o restante da API exige autenticação.

### Fluxo

```text
Cliente --POST /auth/login--> AuthService --valida credenciais--> JWT --> Cliente
```

Nas requisições protegidas:

```http
Authorization: Bearer <token>
```

## Regras de negócio

- a senha é armazenada **somente** com hash BCrypt, nunca em texto puro;
- e-mail já cadastrado resulta em `ConflictException` (409);
- credenciais inválidas resultam em **401**, com mensagem genérica — a resposta não
  deve revelar se o e-mail existe;
- requisição sem token ou com token inválido/expirado em endpoint protegido: **401**;
- usuário autenticado sem permissão sobre o recurso: **403**.

## Validações

- `name` obrigatório no registro;
- `email` obrigatório e em formato válido;
- `password` com tamanho mínimo.

## Configuração

`JWT_SECRET` será lida de variável de ambiente, **sem valor padrão** — a aplicação
não deve subir com um secret embutido no código. O tempo de expiração do token
também será configurável.

## Pontos em aberto

- Biblioteca de JWT a utilizar;
- tempo de expiração do token;
- se haverá refresh token (fora do escopo do MVP até que se mostre necessário);
- como o handler global de erros passará a cobrir 401 e 403: as exceções do Spring
  Security são lançadas em filtros, antes do `@RestControllerAdvice`, e exigem
  `AuthenticationEntryPoint` e `AccessDeniedHandler` próprios para manter o formato
  `ApiError` — ver [shared](shared.md#formato-de-erro).
