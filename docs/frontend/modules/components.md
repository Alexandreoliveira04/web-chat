# Componentes (`components/`)

Todos são componentes de apresentação: recebem dados e callbacks por props de
[`app/chat/page.tsx`](pages.md#chat--tela-principal), que é o dono do estado, e guardam só o
estado local de edição, seleção e formulário. Três chamadas à API ficam no próprio
componente, porque o resultado não interessa a mais ninguém: o upload de arquivo no
`ChatWindow`, a foto de perfil no `ProfileModal` e a busca do conteúdo compartilhado no
`ChatDetailsSidebar`. A foto do grupo é enviada pela página, que recarrega a conversa.

| Componente | Papel |
| ---------- | ----- |
| `Sidebar` | perfil, busca, lista de conversas, colaboradores sem conversa, novo grupo, barra da chamada |
| `ChatWindow` | cabeçalho interativo, botão de ligar, busca, histórico, upload de arquivos e composer |
| `ChatDetailsSidebar` | dados do contato e abas Mídia, Docs e Links com tudo o que foi compartilhado |
| `MessageItem` | bolha de mensagem: autor, texto, foto, vídeo ou documento, e controles (editar/apagar) |
| `DocumentCard` | card de documento: tipo, nome original e link para abrir |
| `IncomingCall` | modal de chamada recebida, com atender e recusar |
| `CallBar` | barra "Voz conectada" no rodapé da lista, com tempo, mudo e desligar |
| `NewGroupModal` | criação de grupo |
| `GroupSettingsModal` | foto e nome do grupo, adicionar, remover e sair |
| `ProfileModal` | editar o próprio nome e a foto de perfil |
| `AvatarPicker` | foto redonda clicável com recorte e zoom, usada no perfil e no grupo |
| `Modal` | casca dos modais |
| `Avatar` | foto ou iniciais + indicador de online |

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

A busca vai ao servidor (`GET /chats?search=`) meio segundo depois de parar de digitar. O
callback `onSearch` precisa ser estável (`useCallback` na página): o efeito da busca depende
dele, e uma função nova a cada render refazia a busca sem parar.

A prop `footer` recebe a [`CallBar`](#callbar) durante uma chamada, fixa no rodapé da
lista, como o "Voz conectada" do Discord.

## `ChatWindow`

Cabeçalho com título, avatar e um botão "mídias, imagens e links". A área superior é inteira
clicável para abrir o [painel de detalhes](#chatdetailssidebar). Em conversa individual, o
botão de telefone inicia uma chamada de voz; ele fica desabilitado enquanto já houver uma
chamada.

- **Busca na conversa**: filtra as mensagens **já carregadas** por conteúdo, mostra
  `atual/total`, navega com ▲/▼ e rola até a mensagem, destacando-a por um segundo. Não
  consulta o servidor — o que não foi carregado pelo histórico não entra na busca.
- **Histórico**: botão "Carregar mensagens anteriores" enquanto `hasMore`; a rolagem vai ao
  fim quando a conversa muda ou chega mensagem nova.
- **Composer**: o clipe abre o seletor de arquivos (fotos, vídeos, PDF, documentos do Office
  e do LibreOffice, texto, planilhas e compactados). O arquivo vai para o Cloud Storage por
  `filesApi.upload` e a URL devolvida é enviada como mensagem. Tem seletor de emoji
  (`emoji-picker-element`) e campo com `maxLength` 2000.
- A janela usa `min-w-0` para encolher de verdade quando o espaço diminui; sem isso, o
  cabeçalho empurrava a largura e as mensagens eram cortadas na borda.

## `ChatDetailsSidebar`

Painel de dados do contato (ou do grupo), aberto pelo cabeçalho do `ChatWindow`.

**Conteúdo.** Ao abrir, busca no servidor tudo o que foi compartilhado na conversa
(`messagesApi.shared`), e não só as mensagens já carregadas no chat, que vêm paginadas. Junta
com as mensagens carregadas, que têm a versão mais recente (novas, editadas ou apagadas), e
classifica cada uma com [`attachmentOf`](libs.md#attachmentsts), a mesma função que o
`MessageItem` usa.

**Abas**, cada uma com a contagem:

- **Mídia**: fotos em grade e vídeos com capa. Clicar abre o visualizador em tela cheia, com
  setas para navegar e contador ("1 de 5").
- **Docs**: um [`DocumentCard`](#documentcard) por documento, com o nome original.
- **Links**: links enviados em mensagens de texto.

**Tamanho da tela.** A partir de 1280 px, o painel fica fixo ao lado do chat (`w-80`). Abaixo
disso, abre **por cima** do chat, ocupando até `max-w-sm` (a tela toda no celular), com o
fundo escurecido; clicar fora ou no ✕ fecha. Antes ele ficava sempre ao lado e espremia o
chat em janelas menores.

## `MessageItem`

Alinha a bolha à direita quando a mensagem é própria. Em grupo, mostra o nome de quem
enviou antes do texto.

- **Anexos**: [`attachmentOf`](libs.md#attachmentsts) diz se o conteúdo é uma foto (vira
  `<img>`), um vídeo (`<video controls>`) ou um documento (vira um
  [`DocumentCard`](#documentcard)). Anexos não têm o botão de editar.
- **Editar** e **apagar** só aparecem para o autor, no hover, e somem em mensagem apagada.
  A edição vira um `textarea` inline; salvar sem mudar nada apenas fecha, sem chamar a API.
- **Estado**: "editada" quando há `editedAt`; "mensagem apagada" em itálico quando há
  `deletedAt` — a mensagem continua ocupando seu lugar no histórico, como no backend.
- **✓ / ✓✓**: `lastReadByOthersMessageId >= message.id` decide. Como o backend só avança
  esse marcador quando **todos** os outros leram, em grupo o ✓✓ é de leitura coletiva.
- Emojis são envolvidos em `<span class="emoji-icon">` para poderem receber um tamanho
  próprio, sem inflar o resto do texto.

## `DocumentCard`

Link para o documento, que abre numa nova aba. Mostra um ícone com a extensão (`PDF`,
`DOCX`...), o nome original do arquivo e "Documento PDF · abrir". A versão `compact` é a do
painel de detalhes.

## `IncomingCall`

Modal centralizado com a foto de quem liga, "está te ligando…" e os botões de recusar
(vermelho) e atender (verde). Aparece enquanto a chamada está no estado `incoming`; o toque
é tocado pelo hook, não pelo componente.

## `CallBar`

Barra no rodapé da lista de conversas durante uma chamada que não seja a recebida:

- "Chamando…" (amarelo), "Conectando…" ou "Voz conectada · 1:23" (verde), com o nome e a
  foto do contato; clicar abre a conversa;
- **mudo**: desliga o envio do microfone (`track.enabled = false`), sem encerrar a chamada;
- **desligar**.

O tempo é calculado a partir de `startedAt`, atualizado a cada segundo só enquanto a chamada
está ativa.

## `NewGroupModal`

Nome do grupo, busca de colaboradores e seleção por checkbox. O botão fica desabilitado sem
nome ou sem ninguém selecionado e mostra a contagem final ("Criar grupo com 4
participantes") — o `+1` é o próprio usuário, que o backend adiciona como dono.

## `GroupSettingsModal`

O conteúdo depende de quem abre: **trocar a foto**, **renomear** e **remover participante**
só aparecem para o dono (os outros veem a foto, sem poder clicar), e o dono não pode se remover — para sair do próprio grupo ele usa "Sair do grupo",
que transfere a posse no backend. Todo participante vê a lista com o rótulo "dono" e o botão
de sair.

O modal não replica as regras do servidor: ele esconde o que não se aplica e exibe em
vermelho a mensagem de erro que a API devolver.

## `ProfileModal`

Edita o próprio nome (`PUT /users/me`, no botão "Salvar Alterações") e a foto de perfil, pelo
[`AvatarPicker`](#avatarpicker): a foto é salva na hora, no "Salvar foto", em
`POST /users/me/avatar`. Também oferece "excluir conta", que é **simulação do frontend**: o
backend não tem rota de remoção de usuário, e a ação só exibe os confirmadores e a mensagem
final.

## `AvatarPicker`

Mostra a foto atual; clicar abre o seletor de imagem. A imagem escolhida vai para um recorte
redondo com zoom (`react-easy-crop`), e "Salvar foto" gera o recorte em JPEG
(`lib/cropImage.ts`) e chama `onSave(file)`, mostrando "Salvando..." e o erro, se houver.
Quem usa decide para onde a foto vai: o `ProfileModal` envia para o perfil, e o
`GroupSettingsModal`, para o grupo. O recorte era código do `ProfileModal` e foi extraído
para os dois usarem.

## `Modal` e `Avatar`

`Modal` é a casca comum: fundo escurecido, fecha no clique fora ou no ✕, e `stopPropagation`
no conteúdo para o clique interno não fechar.

`Avatar` mostra a foto (`url`) ou, sem ela, as iniciais (`initials`); com `online`, o ponto
verde. Todos os lugares que mostram uma pessoa passam a foto: lista de conversas,
colaboradores, cabeçalho, painel de detalhes, mensagens de grupo, modais e chamada. A foto da
conversa vem de `chatAvatarUrl`: a do grupo, ou a do contato em conversa individual. Se a imagem não
carregar, por exemplo uma foto antiga que ficava no disco do servidor, volta para as
iniciais.
