# Módulo `call`

**Pacote:** `br.edu.webchat.call`
**Situação:** implementado — chamada de voz individual (conversas `DIRECT`)

Sinalização das chamadas de voz. **O áudio não passa pelo servidor**: ele vai direto de um
navegador para o outro por WebRTC. O backend só apresenta os dois lados um ao outro, ou seja,
avisa que alguém está ligando e repassa as descrições de mídia (SDP) e os candidatos de rede
(ICE) que cada navegador gera.

## Estrutura

```text
call/
├── controller/   CallController
├── dto/          CallEventResponse, CallEventType, CallEndReason,
│                 CallSignalRequest, CallSignalResponse, SignalType
├── service/      CallService, IceServersService
└── websocket/    CallWebSocketController
```

Não há entidade, repositório nem migration: uma chamada só existe enquanto as duas pontas
estão conectadas, então o estado fica em memória, no `CallService`.

## Fluxo

```text
Ana (liga)                         servidor                        Bia (recebe)
    │ /app/calls/{chatId}/start  ─►  valida, guarda RINGING   ─► /user/queue/calls  RINGING
    │ ◄─ /user/queue/calls RINGING                                   │
    │                                                                │ /app/calls/{chatId}/accept
    │ ◄─ /user/queue/calls ACCEPTED ◄─ marca ativa  ──────────►  ACCEPTED
    │ /app/calls/{chatId}/signal OFFER  ─► repassa ─► /user/queue/call-signal
    │ ◄─ /user/queue/call-signal ANSWER ◄─ repassa ◄─ /app/calls/{chatId}/signal ANSWER
    │ ◄──────────── candidatos ICE nos dois sentidos, do mesmo jeito ────────────►│
    │                                                                             │
    │ ════════════════ áudio direto entre os navegadores (WebRTC) ════════════════│
    │ /app/calls/{chatId}/end  ─►  encerra ─► ENDED para os dois
```

Os papéis são fixos: quem ligou cria a oferta (`OFFER`) depois do `ACCEPTED`; quem atendeu
responde (`ANSWER`). Por isso não existe a disputa de duas ofertas ao mesmo tempo.

## Servidores STUN/TURN

```http
GET /api/v1/calls/ice-servers      (exige login)
```

Devolve a lista `iceServers` que o navegador passa ao `RTCPeerConnection`:

```json
[
  { "urls": "stun:stun.relay.metered.ca:80" },
  { "urls": "turn:global.relay.metered.ca:443", "username": "…", "credential": "…" }
]
```

- O **STUN** só descobre o endereço público de cada aparelho. Quando a conexão direta é
  bloqueada (4G, firewalls, NAT restritivo), o áudio precisa passar por um **TURN**, que o
  repassa. Sem ele, a chamada fica em "Conectando…" e encerra.
- A lista vem da variável **`ICE_SERVERS`** (`app.webrtc.ice-servers`), em JSON, porque o TURN
  tem credenciais e o repositório é público. No deploy ela fica no Secret Manager (ver
  [deploy](../../deploy.md)). Sem a variável, vale só o STUN público do Google.
- O `IceServersService` valida a lista na subida: JSON inválido, lista vazia ou servidor sem
  `urls` impedem a aplicação de subir, para o erro aparecer no deploy e não no meio de uma
  chamada.
- A rota exige login, mas qualquer usuário logado vê as credenciais: o navegador precisa
  delas para usar o TURN. O provedor limita o uso pela própria cota.

## WebSocket

Mesma conexão STOMP do chat (ver [chat](chat.md#websocket)), com a mesma autenticação.

| Frame | Destino | Payload | Quem recebe |
| ----- | ------- | ------- | ----------- |
| `SEND` | `/app/calls/{chatId}/start` | — | — |
| `SEND` | `/app/calls/{chatId}/accept` | — | — |
| `SEND` | `/app/calls/{chatId}/end` | — | — |
| `SEND` | `/app/calls/{chatId}/signal` | `{ "type": "OFFER" \| "ANSWER" \| "ICE", "data": {...} }` | — |
| `SUBSCRIBE` | `/user/queue/calls` | `CallEventResponse` | quem ligou e quem recebe |
| `SUBSCRIBE` | `/user/queue/call-signal` | `CallSignalResponse` | só a outra ponta da chamada |

```json
// /user/queue/calls
{ "type": "ENDED", "chatId": 1, "callerId": 2, "calleeId": 3, "reason": "DECLINED" }

// /user/queue/call-signal
{ "chatId": 1, "fromId": 2, "type": "OFFER", "data": { "type": "offer", "sdp": "v=0..." } }
```

O conteúdo de `data` é gerado pelo navegador e repassado **sem interpretação**: o servidor
não lê o SDP.

### Eventos e motivos de encerramento

| `type` | Quando |
| ------ | ------ |
| `RINGING` | alguém ligou; os dois recebem |
| `ACCEPTED` | quem recebeu atendeu; os dois recebem |
| `ENDED` | a chamada terminou; o motivo vem em `reason` |

| `reason` | Quando | Quem recebe |
| -------- | ------ | ----------- |
| `CANCELED` | quem ligou desistiu antes de ser atendido | os dois |
| `DECLINED` | quem recebeu recusou | os dois |
| `HUNG_UP` | alguém desligou uma chamada em andamento | os dois |
| `BUSY` | a outra pessoa já está em outra chamada | só quem ligou |
| `UNAVAILABLE` | a outra pessoa está offline | só quem ligou |
| `DISCONNECTED` | a conexão de uma das abas da chamada caiu | os dois |

## Regras

- Só em conversa **individual** (`DIRECT`), e só entre participantes dela.
- Cada usuário participa de **no máximo uma** chamada. Ligar estando em outra chamada → `409`.
- Só quem recebeu pode atender (`400` caso contrário).
- A chamada fica presa à **sessão STOMP** (aba) que ligou e à que atendeu:
  - sinais WebRTC só são aceitos dessas duas sessões (`400` para outra aba do mesmo usuário);
  - se uma delas desconectar, a chamada termina com `DISCONNECTED`.
- O toque vai para todas as abas de quem recebe. Quando uma atende, as outras param de tocar
  ao receber o `ACCEPTED`.
- Não há tempo limite no servidor: o frontend de quem liga desiste depois de 30 segundos.

Erros voltam só para a sessão que enviou, em `/user/queue/errors`, pelo tratamento comum
de [shared](shared.md#erros-no-websocket): `400` (inclusive `type` de sinal desconhecido),
`403` (não participa da conversa), `404` (nenhuma chamada em andamento) e `409` (já está em
chamada).

## Decisões de implementação

**Estado em memória.** O `CallService` mantém um `Map` de e-mail → chamada, com os métodos
`synchronized` (o mesmo padrão do `PresenceService`). Persistir não faria sentido: se o
servidor reinicia, as conexões caem e as chamadas acabam de qualquer forma.

**Ocupado e offline não são erro.** Ligar para quem está ocupado ou offline é uma situação
normal, então volta como `ENDED` com o motivo, e não em `/user/queue/errors`.

**Servidor só sinaliza.** Transportar o áudio pelo Spring exigiria um servidor de mídia;
com WebRTC ponto a ponto, o backend continua sendo um monólito simples, e o áudio vai
criptografado (DTLS-SRTP) direto entre os navegadores.

## Testes

| Classe | Tipo | Cobre |
| ------ | ---- | ----- |
| `IceServersServiceTest` | unitário | sem configuração usa só STUN; lista configurada devolvida como veio; JSON inválido, objeto, lista vazia ou servidor sem `urls` impedem a subida |
| `CallControllerTest` | `@WebMvcTest` | a rota devolve a lista de servidores |
| `CallServiceTest` | unitário (Mockito) | toca para os dois; offline (`UNAVAILABLE`); ocupado (`BUSY`); não liga duas vezes nem em grupo; só quem recebe atende; motivo do fim conforme quem e quando; sinal só entre as abas da chamada; queda de aba encerra e aba desconhecida não |
| `CallIntegrationTest` | `@SpringBootTest(RANDOM_PORT)` + cliente STOMP real | chamada completa (toca, atende, oferta e resposta, desliga); queda de conexão; offline e ocupado; erros `403`/`404`/`400` só para quem enviou |

`CallIntegrationTest` estende `StompIntegrationTest`, a base comum dos testes de tempo real.

## Limitações conhecidas

- **Só chamadas individuais.** Chamada em grupo exigiria uma conexão por par de
  participantes (até ~4–5 pessoas) ou um servidor de mídia (SFU), como o LiveKit.
- **TURN com cota.** No deploy, o TURN é o plano grátis da Metered (500 MB de áudio
  repassado). Ao acabar, as chamadas entre redes restritivas voltam a não conectar até
  trocar de provedor ou de plano; muda só a variável `ICE_SERVERS`.
- **Microfone exige contexto seguro.** O navegador só libera o microfone em HTTPS ou em
  `localhost`.
- **Uma instância só.** Como o broker e a presença, o estado das chamadas vive na memória do
  processo.
- **Sem histórico.** Chamadas não ficam registradas na conversa.
