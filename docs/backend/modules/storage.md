# Módulo `storage`

**Pacote:** `br.edu.webchat.storage`
**Situação:** implementado

Recebe os arquivos enviados pelo frontend (fotos, vídeos e documentos do chat, e a foto de
perfil) e os grava no **Google Cloud Storage (GCS)**. O banco de dados nunca recebe o arquivo:
o módulo devolve só a URL, e quem chamou decide onde guardá-la.

## Estrutura

```text
storage/
├── config/       GcsConfig
├── controller/   FileController
├── dto/          FileUploadResponse
└── service/      FileStorageService
```

## Stack e integração

Usa o **SDK oficial do Google Cloud** (`google-cloud-storage`), direto no backend Java.

Em desenvolvimento e no Docker Compose, o módulo fala com o emulador
**`fsouza/fake-gcs-server`**, que implementa a mesma API do Cloud Storage. Para usar o GCS de
verdade, nenhuma linha de código muda: basta configurar o host e as credenciais do Google
Cloud no servidor.

- `GcsConfig`: cria o bean `Storage`, apontando para `gcs.host`.
- `FileStorageService`: grava os bytes no bucket e monta a URL pública. Na inicialização,
  cria o bucket se ele ainda não existir.
- `FileController`: expõe o upload para o frontend.

## API REST

### `POST /api/v1/files/upload`

- **Autorização:** Bearer token (qualquer papel)
- **Body:** `multipart/form-data`, campo `file` (até 10 MB)
- **Resposta `200`:**

```json
{
  "url": "http://localhost:4443/storage/v1/b/webchat-files/o/123e4567-e89b-12d3-a456-426614174000%2FRelat%C3%B3rio%20final.pdf?alt=media"
}
```

O frontend envia essa URL como o conteúdo de uma mensagem comum.

As fotos de perfil e de grupo usam o mesmo serviço pelas rotas dos próprios módulos
([user](user.md#foto-de-perfil) e [chat](chat.md#grupos)), por meio de `uploadImage`, que
recusa com `400` o que não for imagem antes de gravar.

## Nome dos arquivos

Cada arquivo é gravado como `<uuid>/<nome original>`:

- o **UUID** funciona como uma pasta única e evita colisão entre dois arquivos com o mesmo
  nome;
- o **nome original** é mantido como o usuário enviou, com acentos e espaços. É ele que
  aparece no card de documento do chat e no download.

Do nome original, o serviço remove apenas caminhos de pasta (`C:\Users\...`, `../`) e
caracteres de controle, e limita o tamanho a 150 caracteres preservando a extensão. Um nome
vazio vira `arquivo`.

Na URL, o nome do objeto vai codificado num único segmento (a `/` vira `%2F`), como a API do
Cloud Storage exige. O objeto também é gravado com
`Content-Disposition: inline; filename*=UTF-8''<nome>`, para que o navegador use o nome
original ao baixar.

Arquivos enviados antes dessa regra têm só o UUID no nome (`<uuid>.pdf`) e continuam
funcionando.

## URLs no Docker e no host

Dentro do Docker, o backend alcança o emulador por um nome interno da rede (`gcs`), mas o
navegador do usuário precisa de um endereço acessível de fora (`localhost`). Por isso são
duas propriedades:

| Propriedade | Variável | Padrão | Uso |
| ----------- | -------- | ------ | --- |
| `gcs.host` | `GCS_HOST` | `http://localhost:4443` | onde o backend grava (no Compose, `http://gcs:4443`) |
| `gcs.public-url` | `GCS_PUBLICURL` | `http://localhost:4443` | base da URL devolvida ao frontend |
| `gcs.bucket` | `GCS_BUCKET` | `webchat-files` | bucket usado |
| `gcs.init-bucket` | `GCS_INITBUCKET` | `true` | cria o bucket na subida; `false` no perfil `test` e no deploy |
| `gcs.project-id` | `GCS_PROJECTID` | `test-project` | projeto do Google Cloud; no emulador, qualquer valor serve |

No Google Cloud, `GCS_HOST` e `GCS_PUBLICURL` são `https://storage.googleapis.com`, e as
credenciais vêm da conta de serviço do Cloud Run (ver [deploy](../../deploy.md)).

As propriedades não estão no `application.yml`: os padrões ficam no próprio código
(`@Value`), e as variáveis seguem a conversão do Spring (pontos viram `_` e o hífen some).

## Testes

| Classe | Tipo | Cobre |
| ------ | ---- | ----- |
| `FileStorageServiceTest` | unitário (Mockito, `Storage` falso) | `uploadImage` só aceita imagem não vazia; nome `<uuid>/<original>`, tipo e `Content-Disposition` gravados, URL codificada; limpeza do nome (caminhos, controle, acentos mantidos, tamanho); arquivo sem extensão; falha do storage; criação do bucket só quando falta e só se habilitada |
| `FileControllerTest` | `@WebMvcTest` | o upload devolve a URL do arquivo |

Nenhum teste precisa do emulador: o perfil `test` desliga a criação do bucket e o upload é
testado com um `Storage` falso.

## Limitações conhecidas

- **Sem validação de tipo no upload do chat.** Qualquer arquivo de até 10 MB é aceito; só as
  fotos de perfil e de grupo exigem imagem.
- **Arquivos não são apagados.** Apagar uma mensagem ou trocar a foto de perfil não remove o
  objeto antigo do bucket.
- **URLs públicas.** Quem tiver a URL acessa o arquivo, sem token. Em produção, o caminho
  seria usar URLs assinadas com validade.
