# Web Chat — Especificação do Projeto

**Versão:** 1.0  
**Status:** Inicial  
**Backend:** Java 17 + Spring Boot + Maven  
**Frontend:** Next.js (planejado)  
**Banco:** PostgreSQL  
**Ambiente local:** Docker Compose  
**Deploy futuro:** Google Cloud

---

## 1. Visão geral

O Web Chat é uma aplicação web de comunicação interna entre colaboradores de uma organização.

O sistema permitirá que usuários autenticados encontrem outros colaboradores, iniciem conversas individuais e troquem mensagens em tempo real.

O projeto será desenvolvido como um **monólito modular**, utilizando **Spring Boot**, **MVC**, **REST API** e **WebSocket**.

O frontend será desenvolvido posteriormente em Next.js. Nesta etapa, o foco principal é o backend.

---

## 2. Objetivo

Construir uma aplicação simples de chat interno que demonstre:

- desenvolvimento de uma API REST;
- arquitetura MVC;
- organização modular;
- autenticação e autorização;
- persistência em PostgreSQL;
- comunicação em tempo real com WebSocket;
- execução local com Docker;
- preparação para deploy em cloud.

O projeto deve priorizar simplicidade, clareza e facilidade de manutenção, evitando overengineering.

---

## 3. Escopo do MVP

### 3.1 Autenticação

O sistema deverá permitir:

- cadastro de usuário;
- login;
- autenticação utilizando JWT;
- acesso somente a recursos autenticados;
- identificação do usuário autenticado;
- autorização por papéis (`USER` e `ADMIN`).

### 3.2 Usuários

O sistema deverá permitir:

- listar colaboradores;
- consultar um colaborador;
- consultar o próprio perfil;
- atualizar dados básicos do próprio perfil;
- representar o status do usuário como online/offline;
- administradores atualizarem dados básicos de qualquer colaborador;
- administradores alterarem o papel de outros colaboradores.

### 3.3 Conversas

O sistema deverá permitir:

- iniciar uma conversa individual;
- listar as conversas do usuário autenticado;
- consultar os participantes de uma conversa;
- consultar o histórico de mensagens.

O MVP não terá grupos.

### 3.4 Mensagens

O sistema deverá permitir:

- enviar mensagens;
- receber mensagens em tempo real;
- armazenar mensagens no banco;
- consultar histórico;
- identificar remetente;
- armazenar data/hora;
- marcar mensagens como lidas.

---

## 4. Fora do escopo inicial

Os seguintes recursos não fazem parte do MVP:

- grupos;
- chamadas de áudio;
- chamadas de vídeo;
- envio de arquivos;
- envio de imagens;
- edição de mensagens;
- exclusão de mensagens;
- respostas/threads;
- reações;
- busca avançada;
- notificações push;
- Redis;
- Kafka;
- RabbitMQ;
- microsserviços;
- Kubernetes;
- CQRS;
- Event Sourcing;
- arquitetura hexagonal;
- DDD complexo.

Esses recursos poderão ser adicionados posteriormente caso sejam necessários.

---

## 5. Stack tecnológica

### Backend

- Java 17
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT
- Bean Validation
- WebSocket
- PostgreSQL
- Docker / Docker Compose

Spring Boot 3 possui Java 17 como requisito mínimo. A versão exata do Spring Boot deverá ser mantida consistente no `pom.xml` durante o desenvolvimento. 

### Frontend

Planejado:

- Next.js
- TypeScript

O frontend não faz parte da primeira etapa de implementação.

### Infraestrutura

Local:

- Docker Compose
- PostgreSQL

Futuro:

- Google Cloud
- PostgreSQL gerenciado ou outra solução compatível
- aplicação Spring Boot containerizada

---

## 6. Arquitetura

A aplicação será um **monólito modular**.

Não serão criados múltiplos serviços ou múltiplos projetos Maven.

Estrutura conceitual:

```text
Next.js
   |
   | REST / WebSocket
   v
Spring Boot
   |
   +-- auth
   +-- user
   +-- chat
   +-- shared
   |
   v
PostgreSQL
```

---

## 7. Padrão MVC

Cada módulo deverá seguir uma organização baseada em MVC.

Fluxo esperado:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
Database
```

### Controller

Responsável por:

- receber requisições;
- validar entrada;
- chamar serviços;
- devolver respostas HTTP.

Controllers não devem conter regras de negócio.

### Service

Responsável por:

- regras de negócio;
- validações de negócio;
- coordenação das operações;
- transações.

### Repository

Responsável pelo acesso aos dados utilizando Spring Data JPA.

### Entity

Representa as entidades persistidas no banco.

### DTO

Responsável pela comunicação entre API e cliente.

Entidades JPA não devem ser expostas diretamente nas respostas da API.

---

## 8. Módulos

### 8.1 AUTH

Responsável pela autenticação e pela autorização.

Responsabilidades:

- registro;
- login;
- geração de JWT;
- validação do JWT;
- integração com Spring Security;
- regras de acesso por papel.

Estrutura:

```text
auth/
├── config/       SecurityConfig, SecurityErrorHandler
├── controller/
├── dto/
├── filter/       JwtAuthenticationFilter
├── jwt/          JwtService
└── service/
```

---

### 8.2 USER

Responsável pelos colaboradores.

Estrutura:

```text
user/
├── config/       AdminInitializer (administrador inicial)
├── controller/
├── dto/
├── entity/
├── repository/
└── service/
```

Responsabilidades:

- cadastro;
- consulta;
- atualização;
- perfil;
- status;
- papel (`USER` / `ADMIN`).

---

### 8.3 CHAT

Responsável por conversas e mensagens.

Estrutura:

```text
chat/
├── controller/
├── dto/
├── entity/
├── repository/
├── service/
└── websocket/
```

Responsabilidades:

- conversas;
- participantes;
- mensagens;
- histórico;
- WebSocket.

---

### 8.4 SHARED

Deve conter somente componentes realmente compartilhados.

Estrutura:

```text
shared/
├── config/
└── exception/
```

Exemplos:

- tratamento global de exceções;
- configurações comuns;
- respostas de erro.

Evitar transformar `shared` em um depósito de classes sem responsabilidade clara.

---

## 9. Estrutura de diretórios

```text
web-chat/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── br/
│   │   │       └── edu/
│   │   │           └── webchat/
│   │   │               ├── WebChatApplication.java
│   │   │               │
│   │   │               ├── auth/
│   │   │               ├── user/
│   │   │               ├── chat/
│   │   │               └── shared/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   │
│   └── test/
│
├── compose.yaml
├── Dockerfile
├── pom.xml
├── README.md
└── .gitignore
```

O package raiz deverá ser ajustado caso o projeto existente utilize outro namespace.

---

## 10. Modelo de dados

### users

```text
id
name
email
password
status
role
created_at
updated_at
```

Regras:

- `id` deve ser gerado automaticamente;
- `email` deve ser único;
- `password` deve ser armazenada somente com hash;
- `status` representa o estado atual do usuário;
- `role` é o papel do usuário (`USER` ou `ADMIN`), com padrão `USER`.

### chats

```text
id
created_at
updated_at
```

### chat_participants

```text
chat_id
user_id
```

Regras:

- um chat individual possui exatamente dois participantes;
- um usuário não pode participar duas vezes da mesma conversa.

### messages

```text
id
chat_id
sender_id
content
created_at
read_at
```

Regras:

- uma mensagem pertence a uma conversa;
- uma mensagem possui exatamente um remetente;
- o conteúdo não pode ser vazio;
- `created_at` é preenchido automaticamente;
- `read_at` pode ser nulo.

---

## 11. Relacionamentos

```text
User
 |
 +----< ChatParticipant >---- Chat
                                  |
                                  +----< Message
                                           |
                                           +---- User (sender)
```

Relacionamentos principais:

- User 1:N ChatParticipant
- Chat 1:N ChatParticipant
- Chat 1:N Message
- User 1:N Message

---

## 12. API REST

Prefixo:

```text
/api/v1
```

### Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

### Users

```http
GET   /api/v1/users
GET   /api/v1/users/{id}
GET   /api/v1/users/me
PUT   /api/v1/users/me
PUT   /api/v1/users/{id}          (ADMIN)
PATCH /api/v1/users/{id}/role     (ADMIN)
```

### Chats

```http
GET /api/v1/chats
POST /api/v1/chats
GET /api/v1/chats/{chatId}
GET /api/v1/chats/{chatId}/messages
POST /api/v1/chats/{chatId}/messages
```

Os endpoints poderão evoluir conforme a implementação.

---

## 13. WebSocket

O WebSocket será utilizado somente para comunicação em tempo real.

REST será utilizado para:

- autenticação;
- consulta de usuários;
- criação/listagem de chats;
- histórico;
- operações persistentes.

WebSocket será utilizado para:

- envio de mensagens em tempo real;
- recebimento de mensagens;
- eventos de chat.

A implementação deverá utilizar uma abordagem compatível com o ecossistema Spring, preferencialmente STOMP sobre WebSocket.

O protocolo e os destinos exatos deverão ser documentados durante a implementação.

---

## 14. Autenticação

O sistema utilizará JWT.

Fluxo:

```text
Cliente
   |
   | POST /auth/login
   v
Spring Boot
   |
   | valida credenciais
   v
JWT
   |
   v
Cliente
```

Nas requisições protegidas:

```http
Authorization: Bearer <token>
```

Senhas nunca devem ser armazenadas em texto puro.

### 14.1 Autorização por papéis

Decisão tomada no Módulo 0, para corrigir a falha em que qualquer usuário autenticado
podia alterar os dados de outro colaborador.

Papéis:

| Papel | Permissões |
| ----- | ---------- |
| `USER` | ler colaboradores, ler e atualizar o **próprio** perfil (`/users/me`), usar o chat |
| `ADMIN` | tudo de `USER`, mais atualizar qualquer colaborador e alterar papéis |

Regras:

- cada usuário possui **um único papel**, armazenado na coluna `users.role`;
- o cadastro público (`POST /auth/register`) cria sempre `USER`; o papel nunca vem do corpo da requisição;
- as regras de acesso por rota ficam no `SecurityConfig` (`hasRole("ADMIN")`); violação resulta em **403**;
- o papel **não** é gravado no JWT: ele é lido do banco a cada requisição, então uma mudança de papel vale imediatamente;
- nenhum usuário pode alterar o próprio papel (403), o que impede que o sistema fique sem administrador;
- o administrador inicial é criado (ou promovido) na inicialização a partir de `ADMIN_EMAIL`, `ADMIN_PASSWORD` e `ADMIN_NAME`; sem `ADMIN_EMAIL`, nada é criado.

Administradores **não** ganham acesso a conversas de terceiros: a regra de participante do chat vale para todos os papéis.

---

## 15. Validação

Utilizar Bean Validation para validações de entrada.

Exemplos:

- nome obrigatório;
- email válido;
- senha com tamanho mínimo;
- mensagem não vazia;
- IDs obrigatórios.

As regras de negócio devem permanecer nos services.

---

## 16. Tratamento de erros

A API deverá possuir tratamento global de exceções.

Erros devem retornar respostas HTTP adequadas.

Exemplos:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
500 Internal Server Error
```

As respostas de erro devem possuir estrutura consistente.

---

## 17. Configuração

As configurações específicas de ambiente não devem ficar hardcoded.

Exemplos:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
JWT_SECRET
ADMIN_NAME
ADMIN_EMAIL
ADMIN_PASSWORD
```

O arquivo `application.yml` deverá utilizar variáveis de ambiente quando apropriado.

Nunca versionar:

- senhas reais;
- secrets;
- tokens;
- credenciais da Google Cloud.

---

## 18. PostgreSQL

O banco de desenvolvimento deverá ser executado através do Docker Compose.

Exemplo:

```yaml
services:
  postgres:
    image: postgres:16
    container_name: web-chat-postgres
    environment:
      POSTGRES_DB: webchat
      POSTGRES_USER: webchat
      POSTGRES_PASSWORD: webchat
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

A configuração poderá ser ajustada de acordo com o ambiente local.

---

## 19. Banco de dados e migrations

O projeto deverá utilizar uma ferramenta de migrations, preferencialmente Flyway.

As migrations devem ser versionadas no Git.

Migrations atuais e previstas:

```text
src/main/resources/db/migration/
├── V1__init.sql                          (aplicada)
├── V2__create_users.sql                  (aplicada)
├── V3__add_role_to_users.sql             (aplicada)
├── V4__create_chats.sql                  (prevista)
├── V5__create_chat_participants.sql      (prevista)
└── V6__create_messages.sql               (prevista)
```

Migrations já aplicadas nunca devem ser editadas; qualquer mudança de schema entra em uma nova versão.

O Hibernate não deverá ser utilizado como mecanismo principal de versionamento do schema em produção.

---

## 20. Testes

O backend deverá possuir testes básicos.

Prioridade:

1. testes unitários dos services;
2. testes dos controllers;
3. testes de repository quando necessário;
4. testes de integração dos principais fluxos.

Fluxos mínimos:

- cadastro;
- login;
- criação de chat;
- envio de mensagem;
- consulta de histórico.

---

## 21. Docker

O projeto deverá possuir:

- `compose.yaml` para PostgreSQL;
- `Dockerfile` para o backend.

Durante o desenvolvimento inicial, não é obrigatório executar o Spring Boot dentro do Docker.

A prioridade é manter o banco containerizado e o backend executando pela IDE/Maven.

---

## 22. Deploy futuro

O sistema deverá ser preparado para deploy futuro na Google Cloud.

Requisitos:

- configuração via environment variables;
- aplicação stateless;
- build Maven reproduzível;
- Dockerfile funcional;
- nenhuma dependência de caminhos locais;
- nenhuma credencial armazenada no código.

O serviço exato da Google Cloud será definido posteriormente.

---

## 23. Requisitos não funcionais

### RNF01 — Manutenibilidade

O código deve ser simples e organizado por módulos.

### RNF02 — Segurança

Senhas devem ser armazenadas com hash e endpoints protegidos devem exigir autenticação.

### RNF03 — Performance

Consultas devem evitar carregamentos desnecessários e N+1 quando possível.

### RNF04 — Configuração

Dados específicos do ambiente devem ser configuráveis por variáveis de ambiente.

### RNF05 — Compatibilidade

O backend deve utilizar Java 17.

### RNF06 — Execução

O projeto deve ser executável através do Maven.

### RNF07 — Persistência

Os dados devem permanecer armazenados no PostgreSQL.

---

## 24. Convenções

### Classes

```text
UserController
UserService
UserRepository
UserEntity
UserResponse
UserRequest
```

### Endpoints

Utilizar nomes no plural:

```text
/users
/chats
/messages
```

### IDs

Numéricos: `Long` no Java e `BIGINT` no PostgreSQL, gerados pelo banco
(`GENERATED BY DEFAULT AS IDENTITY` / `GenerationType.IDENTITY`).

Decisão tomada na Fase 2, em favor da simplicidade do projeto. UUID não é
utilizado em nenhuma entidade.

### Datas

Utilizar tipos modernos do Java, como `Instant` ou `OffsetDateTime`, evitando `java.util.Date`.

### JSON

Utilizar `camelCase`.

Exemplo:

```json
{
  "id": 1,
  "createdAt": "2026-09-08T22:00:00Z"
}
```

---

## 25. Roadmap

### Fase 1 — Fundação

- [x] revisar projeto Maven existente;
- [x] configurar Java 17;
- [x] configurar Spring Boot;
- [x] configurar PostgreSQL;
- [x] configurar Docker Compose;
- [x] configurar Flyway;
- [x] organizar pacotes.

### Fase 2 — USER

- [x] entidade User;
- [x] migration;
- [x] repository;
- [x] service;
- [x] DTOs;
- [x] controller;
- [x] validações;
- [x] testes.

### Fase 3 — AUTH

- [x] registro;
- [x] BCrypt;
- [x] login;
- [x] JWT;
- [x] Spring Security;
- [x] proteção dos endpoints;
- [x] testes.

### Fase 3.1 — PAPÉIS E PERMISSÕES (Módulo 0)

- [x] papel `USER` / `ADMIN` na entidade User;
- [x] migration `V3__add_role_to_users.sql`;
- [x] `POST /auth/register` substitui `POST /users`;
- [x] `PUT /users/me` para o próprio perfil;
- [x] `PUT /users/{id}` e `PATCH /users/{id}/role` restritos a ADMIN;
- [x] `ForbiddenException` (403);
- [x] administrador inicial via variáveis de ambiente;
- [x] testes.

### Fase 4 — CHAT

- [ ] entidade Chat;
- [ ] participantes;
- [ ] migrations;
- [ ] repositories;
- [ ] services;
- [ ] controllers;
- [ ] criação de conversas;
- [ ] listagem de conversas.

### Fase 5 — MESSAGE

- [ ] entidade Message;
- [ ] migration;
- [ ] envio;
- [ ] histórico;
- [ ] leitura;
- [ ] testes.

### Fase 6 — WEBSOCKET

- [ ] configuração;
- [ ] conexão;
- [ ] autenticação;
- [ ] envio;
- [ ] recebimento;
- [ ] eventos;
- [ ] testes.

### Fase 7 — FRONTEND

- [ ] projeto Next.js;
- [ ] login;
- [ ] usuários;
- [ ] lista de chats;
- [ ] tela de conversa;
- [ ] WebSocket.

### Fase 8 — DEPLOY

- [ ] Docker;
- [ ] configuração de produção;
- [ ] Google Cloud;
- [ ] banco;
- [ ] variáveis de ambiente;
- [ ] domínio/HTTPS;
- [ ] monitoramento básico.

---

## 26. Critérios de conclusão do MVP

O MVP será considerado concluído quando:

- [x] um usuário puder ser cadastrado;
- [x] um usuário puder realizar login;
- [x] endpoints protegidos exigirem autenticação;
- [ ] dois usuários puderem iniciar uma conversa;
- [ ] mensagens forem persistidas no PostgreSQL;
- [ ] o histórico puder ser consultado;
- [ ] mensagens puderem ser recebidas em tempo real;
- [x] o backend puder ser executado localmente;
- [x] o PostgreSQL puder ser iniciado via Docker Compose;
- [ ] testes básicos estiverem funcionando.

---

## 27. Princípios do projeto

1. **Simplicidade antes de abstração.**
2. **Não implementar funcionalidades fora do escopo sem necessidade.**
3. **Não adicionar tecnologias apenas por serem populares.**
4. **Controllers devem ser finos.**
5. **Regras de negócio devem ficar nos services.**
6. **Entidades não devem ser expostas diretamente pela API.**
7. **Configurações sensíveis devem ficar fora do código.**
8. **Toda alteração relevante deve manter o projeto compilando e testável.**
9. **Cada módulo deve possuir responsabilidade clara.**
10. **A arquitetura deve permanecer adequada ao tamanho do projeto.**

---

## 28. Estado atual

Fases 1, 2 e 3 concluídas, incluindo autorização por papéis (Fase 3.1 / Módulo 0).
Próxima etapa: Fase 4 — CHAT.

O frontend atual é um conjunto de páginas estáticas (HTML/CSS/JS) servidas pelo próprio
Spring Boot em `src/main/resources/static`, usado para demonstrar a API. O frontend
Next.js da Fase 7 continua planejado.

Alterações arquiteturais ou funcionais relevantes devem ser registradas nesta especificação antes de serem implementadas.

O desenvolvimento deve ocorrer de forma incremental, seguindo o roadmap.
