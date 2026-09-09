# Módulo `user`

**Pacote:** `br.edu.webchat.user`
**Fase:** 2
**Situação:** não iniciado — o pacote existe, sem classes

Responsável pelos colaboradores: cadastro, consulta, perfil e status.

> Este documento descreve o contrato planejado a partir da
> [WEB-CHAT-SPEC.md](../WEB-CHAT-SPEC.md). Nomes de campos de DTO e detalhes de
> implementação podem ser ajustados durante a fase 2 — o documento deve ser
> atualizado junto com o código.

## Estrutura prevista

```text
user/
├── controller/   UserController
├── dto/          UserRequest, UserResponse
├── entity/       UserEntity
├── repository/   UserRepository
└── service/      UserService
```

## Responsabilidades

- cadastro de colaborador (a criação em si é acionada pelo registro em [auth](auth.md));
- listagem de colaboradores;
- consulta de um colaborador por id;
- consulta do próprio perfil;
- atualização dos dados básicos do próprio perfil;
- representação do status online/offline.

## Modelo de dados

Tabela `users`:

| Coluna | Tipo | Observações |
| ------ | ---- | ----------- |
| `id` | UUID | PK, gerado automaticamente |
| `name` | texto | obrigatório |
| `email` | texto | **único**, obrigatório |
| `password` | texto | somente hash (BCrypt); nunca em texto puro |
| `status` | texto | online/offline |
| `created_at` | timestamptz | preenchido automaticamente |
| `updated_at` | timestamptz | |

Migration: `V2__create_users.sql` (a numeração segue a última migration existente).

> `password` fica na tabela `users`, mas seu tratamento — hash e verificação — é
> responsabilidade do módulo [auth](auth.md).

## API

| Método | Rota | Descrição | Autenticação |
| ------ | ---- | --------- | ------------ |
| `GET` | `/api/v1/users` | Lista colaboradores | Sim |
| `GET` | `/api/v1/users/{id}` | Consulta um colaborador | Sim |
| `GET` | `/api/v1/users/me` | Perfil do usuário autenticado | Sim |
| `PUT` | `/api/v1/users/me` | Atualiza o próprio perfil | Sim |

A proteção efetiva desses endpoints só passa a existir na fase 3, quando o Spring
Security for configurado.

### Resposta

`UserResponse` **nunca** inclui `password`:

```json
{
  "id": "3f2a9c1e-...",
  "name": "Alexandre Oliveira",
  "email": "alexandre@exemplo.com",
  "status": "ONLINE",
  "createdAt": "2026-09-09T02:00:00Z"
}
```

## Regras de negócio

- `email` é único; tentativa de reuso resulta em `ConflictException` (409);
- id inexistente resulta em `NotFoundException` (404);
- `PUT /users/me` altera apenas os dados básicos do próprio usuário — nunca o de
  outro colaborador;
- a senha não é alterada por esse endpoint.

## Validações

- `name` obrigatório;
- `email` obrigatório e em formato válido;
- `password` com tamanho mínimo (definido na fase 3, junto com o registro).

## Pontos em aberto

- Representação de `status`: enum Java persistido como texto é o caminho provável,
  mas a decisão fica para a fase 2.
- Como o status online/offline é atualizado: provavelmente por eventos de conexão do
  WebSocket (fase 6). Até lá, o campo existe mas não é mantido em tempo real.
