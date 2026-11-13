# Próximos passos

O que ficou pendente ou foi planejado, em ordem sugerida. Atualizado em novembro de 2026,
depois das chamadas de voz individuais, da foto de perfil no Cloud Storage e da galeria com
documentos.

## Antes de juntar a branch atual na `develop`

A branch `feat/chamada-de-voz` já está no GitHub, com pull request aberto para a `develop`.

- [x] **Testar a chamada com microfone de verdade**, em dois navegadores e com fone.
- [ ] **Avisar o grupo sobre as fotos de perfil antigas.** Elas ficavam no disco do servidor e
  deixaram de aparecer (o avatar mostra as iniciais); cada pessoa precisa enviar a foto de
  novo. Depois disso, a pasta `backend/uploads/` pode ser apagada.
- [x] **Não versionar as pastas `web-chat/` e `__MACOSX/`** que estão soltas na raiz do
  repositório (parecem de um zip extraído). Ficaram fora dos commits, mas continuam no disco.
- [x] **Commit e pull request** para `develop`.
- [ ] **Atualizar o documento da disciplina:** agora são seis módulos (entraram `storage` e
  `call`), 208 testes, as fotos de perfil e de grupo também vão para o Cloud Storage e há chamadas de voz
  por WebRTC (referência: RFC 8825).

## Chamadas de voz

- [ ] **Toque próprio.** O toque atual é gerado por código (`frontend/lib/ringtone.ts`). Para
  usar uma gravação, coloque os arquivos em `frontend/public/sounds/` e troque as chamadas de
  `playRingtone` em `useVoiceCall.ts` por uma função que toque o arquivo em loop. Ver
  [lib](frontend/modules/libs.md#ringtonets).
- [x] **Servidor TURN** (Metered, plano grátis), para o áudio conectar também atrás de redes
  restritivas. Se a cota de 500 MB não bastar, trocar de provedor (por exemplo o TURN da
  Cloudflare) muda só o arquivo de `ICE_SERVERS`.
- [ ] **Chamada em grupo.** Primeiro com uma conexão por par de participantes (funciona bem
  até umas 4–5 pessoas), com sala de voz por conversa, como no Discord: entra e sai quando
  quiser, e todos veem quem está na sala. Para grupos grandes, trocar por um servidor de
  mídia (SFU), como o LiveKit.
- [ ] **Indicador de quem está falando**, medindo o volume com um `AnalyserNode` do Web Audio
  e acendendo o avatar.
- [ ] **Histórico de chamadas** na conversa ("Chamada de voz · 12 min").
- [ ] **Compartilhar tela**, com `getDisplayMedia`, no mesmo fluxo da chamada.

## Jogos na conversa

Planejado: uma aba de jogos para jogar com quem está na conversa.

- [ ] **Módulo `game` no backend**, com o servidor como autoridade: o navegador só envia a
  jogada, e o servidor valida a vez e a regra, detecta vitória, salva e avisa todos os
  participantes por STOMP.
  - Tabela `games` (migration `V12`) com o estado em JSONB e `@Version` para jogadas
    simultâneas.
  - Interface `GameEngine` (`initialState`, `applyMove`, `result`): cada jogo novo é uma
    classe nova.
  - Teste de integração estendendo `StompIntegrationTest`.
- [ ] **Jogo da velha** primeiro, de ponta a ponta; depois Lig 4 (2 pessoas) e forca ou quiz
  (grupo).
- [ ] **Painel de jogos** no `ChatDetailsSidebar` ou um botão no cabeçalho da conversa
  (decisão em aberto).

## Arquivos

- [ ] **Validar o tipo no upload do chat.** Hoje qualquer arquivo de até 10 MB é aceito.
- [ ] **Apagar do Cloud Storage** o arquivo de uma mensagem apagada e as fotos de perfil e de
  grupo substituídas.
- [ ] **URLs assinadas com validade** em vez de URLs públicas, antes de ir para produção.

## Qualidade

- [ ] **Corrigir os 3 erros antigos de lint do frontend** (uma ref atribuída durante o render,
  um `any` e um `@ts-ignore`).
- [ ] **Testes de frontend**: Testing Library para os componentes e Playwright para o fluxo
  completo, incluindo dois usuários conversando.
- [ ] **Testcontainers com PostgreSQL** nos testes de integração, para validar o SQL real em
  vez do H2.

## Produção

- [ ] **Publicar no Google Cloud** com o script `deploy/gcp.sh` (Cloud Run, Cloud SQL e
  Cloud Storage, com HTTPS automático). Ver [deploy](deploy.md).
- [ ] **Deploy automático** a cada merge (GitHub Actions ou gatilho do Cloud Build).
- [ ] **Broker externo** (RabbitMQ ou Redis), caso seja preciso mais de uma instância: hoje
  mensagens em tempo real, presença e chamadas vivem na memória de um processo.
- [ ] **Refresh token**, para não precisar de novo login quando o JWT expira.
