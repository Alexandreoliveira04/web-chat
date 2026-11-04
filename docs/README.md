# Documentação

Documentação técnica do Web Chat. Para instalar e rodar, ver o [README](../README.md).

## Backend

API REST + WebSocket em Java 17 e Spring Boot, organizada como monólito modular.

- [Arquitetura](backend/architecture.md) — stack, módulos, camadas, convenções, configuração e testes
- Módulos:
  - [shared](backend/modules/shared.md) — configuração comum, erros (REST e WebSocket), health check
  - [user](backend/modules/user.md) — colaboradores, perfil e foto, status e papel
  - [auth](backend/modules/auth.md) — registro, login, JWT e autorização
  - [chat](backend/modules/chat.md) — conversas, grupos, mensagens, arquivos compartilhados, histórico e WebSocket
  - [storage](backend/modules/storage.md) — upload de arquivos para o Google Cloud Storage
  - [call](backend/modules/call.md) — chamadas de voz individuais (sinalização WebRTC)

## Frontend

Interface em Next.js 16 + TypeScript, publicada como export estático e servida pelo backend.

- [Arquitetura](frontend/architecture.md) — stack, comunicação com a API, tempo real e build
- Módulos:
  - [páginas](frontend/modules/pages.md) — rotas, estado e ciclo de vida
  - [componentes](frontend/modules/components.md) — barra lateral, janela de conversa, painel de detalhes, chamada, modais
  - [lib](frontend/modules/libs.md) — cliente REST, STOMP, chamada de voz, anexos, tipos e formatação

## Próximos passos

- [Próximos passos](roadmap.md) — pendências da branch atual e o que está planejado

Cada documento registra também as decisões tomadas durante a implementação e as limitações
conhecidas da parte que descreve.
