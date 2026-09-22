# Componentes (`components/`)

Todos são componentes de apresentação: recebem dados e callbacks por props de
[`app/chat/page.tsx`](pages.md#chat--tela-principal), que é o dono do estado. Nenhum
deles chama a API por conta própria — a exceção é o estado local de edição, seleção e
formulário, que não interessa a ninguém de fora.

| Componente | Papel |
| ---------- | ----- |
| `Sidebar` | perfil, busca, lista de conversas, colaboradores sem conversa, novo grupo |
| `ChatWindow` | cabeçalho, busca na conversa, histórico, "carregar anteriores" e composer |
| `MessageItem` | bolha da mensagem: autor, ✓/✓✓, editar e apagar |
| `NewGroupModal` | criação de grupo |
| `GroupSettingsModal` | renomear, adicionar, remover e sair |
| `ProfileModal` | editar o próprio nome |
| `Modal` | casca dos modais |
| `Avatar` | iniciais + indicador de online |

## `Sidebar`

Cabeçalho com o próprio avatar, o nome e o estado da conexão ("conectado" /
"reconectando..."), que vem da prop `conectado`. O avatar abre um menu com **Editar
perfil**, **Alternar tema** (alterna a classe `theme-light` no `<html>`) e **Sair**.

Abaixo, um campo de busca e duas listas, ambas filtradas pelo mesmo termo:

1. **Conversas**, ordenadas por última atividade (`sortChats`), com prévia da última
   mensagem, `#` antes do nome de grupo e a bolha de não lidas;
2. **Colaboradores**, só quem ainda não tem conversa individual com o usuário. Clicar
   dispara `onStartDirect`, que cria ou reaproveita a conversa.

Separar as duas listas é o que faz a tela funcionar sem uma "agenda" à parte: quem já tem
histórico aparece em cima; quem nunca conversou, embaixo, e some da segunda lista assim que
a conversa existe.

## `ChatWindow`

Cabeçalho com título e subtítulo da conversa (`chatTitle` / `chatSubtitle`: "online/offline"
em conversa individual, "N participantes" em grupo) e, em grupo, o botão **Participantes**.

- **Busca na conversa**: filtra as mensagens **já carregadas** por conteúdo, mostra
  `atual/total`, navega com ▲/▼ e rola até a mensagem, destacando-a por um segundo. Não
  consulta o servidor — o que não foi carregado pelo histórico não entra na busca.
- **Histórico**: botão "Carregar mensagens anteriores" enquanto `hasMore`; a rolagem vai ao
  fim quando a conversa muda ou chega mensagem nova.
- **Composer**: seletor de emoji (`emoji-picker-element`, importado sob demanda no primeiro
  render e fechado ao clicar fora) e campo com `maxLength` 2000 — o mesmo limite do backend,
  para que o envio não seja recusado depois de o texto já ter sido digitado.

## `MessageItem`

Alinha a bolha à direita quando a mensagem é própria. Em grupo, mostra o nome de quem
enviou antes do texto.

- **Editar** e **apagar** só aparecem para o autor, no hover, e somem em mensagem apagada.
  A edição vira um `textarea` inline; salvar sem mudar nada apenas fecha, sem chamar a API.
- **Estado**: "editada" quando há `editedAt`; "mensagem apagada" em itálico quando há
  `deletedAt` — a mensagem continua ocupando seu lugar no histórico, como no backend.
- **✓ / ✓✓**: `lastReadByOthersMessageId >= message.id` decide. Como o backend só avança
  esse marcador quando **todos** os outros leram, em grupo o ✓✓ é de leitura coletiva.
- Emojis são envolvidos em `<span class="emoji-icon">` para poderem receber um tamanho
  próprio, sem inflar o resto do texto.

## `NewGroupModal`

Nome do grupo, busca de colaboradores e seleção por checkbox. O botão fica desabilitado sem
nome ou sem ninguém selecionado e mostra a contagem final ("Criar grupo com 4
participantes") — o `+1` é o próprio usuário, que o backend adiciona como dono.

## `GroupSettingsModal`

O conteúdo depende de quem abre: **renomear** e **remover participante** só aparecem para o
dono, e o dono não pode se remover — para sair do próprio grupo ele usa "Sair do grupo",
que transfere a posse no backend. Todo participante vê a lista com o rótulo "dono" e o botão
de sair.

O modal não replica as regras do servidor: ele esconde o que não se aplica e exibe em
vermelho a mensagem de erro que a API devolver.

## `ProfileModal`

Edita o próprio nome (`PUT /users/me`). Também oferece "excluir conta", que é **simulação
do frontend**: o backend não tem rota de remoção de usuário, e a ação só exibe os
confirmadores e a mensagem final.

## `Modal` e `Avatar`

`Modal` é a casca comum: fundo escurecido, fecha no clique fora ou no ✕, e `stopPropagation`
no conteúdo para o clique interno não fechar. `Avatar` mostra as iniciais (`initials`) e,
com `online`, o ponto verde — usado na barra lateral, nos modais e no cabeçalho.
