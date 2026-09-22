# Documentação

Documentação técnica do Web Chat. Para instalar e rodar, ver o [README](../README.md).

## Backend

API REST + WebSocket em Java 17 e Spring Boot, organizada como monólito modular.

- [Arquitetura](backend/architecture.md) — stack, módulos, camadas, convenções, configuração e testes
- Módulos:
  - [shared](backend/modules/shared.md) — configuração comum, erros, health check
  - [user](backend/modules/user.md) — colaboradores, perfil, status e papel
  - [auth](backend/modules/auth.md) — registro, login, JWT e autorização
  - [chat](backend/modules/chat.md) — conversas, grupos, mensagens, histórico e WebSocket

## Frontend

Interface em Next.js 16 + TypeScript, publicada como export estático e servida pelo backend.

- [Arquitetura](frontend/architecture.md) — stack, comunicação com a API, tempo real e build
- Módulos:
  - [páginas](frontend/modules/pages.md) — rotas, estado e ciclo de vida
  - [componentes](frontend/modules/components.md) — barra lateral, janela de conversa, modais
  - [lib](frontend/modules/libs.md) — cliente REST, STOMP, tipos e formatação

Cada documento registra também as decisões tomadas durante a implementação e as limitações
conhecidas da parte que descreve.
