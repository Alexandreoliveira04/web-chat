# Módulo Storage

Este módulo é responsável por gerenciar uploads de arquivos, anexos do chat (imagens, vídeos, documentos) e integra-se diretamente com o **Google Cloud Storage (GCS)**.

## Responsabilidades

- Conectar ao Google Cloud Storage (ou ao emulador local).
- Criar e gerenciar o ciclo de vida do bucket de armazenamento do projeto.
- Receber arquivos via requisições HTTP multipart.
- Gerar nomes únicos (`UUID`) para prevenir colisão de arquivos.
- Retornar URLs públicas para os arquivos armazenados.

## Stack e Integração

Foi utilizado o **SDK Oficial do Google Cloud (`google-cloud-storage`)** para que o código seja 100% nativo para nuvem.

Durante o desenvolvimento local (e via Docker Compose), o módulo não se conecta aos servidores reais do Google, mas sim ao emulador **`fsouza/fake-gcs-server`**, que simula a API S3/GCS. Ao ir para produção, nenhuma linha de código precisa ser alterada: apenas a variável de ambiente `GCS_HOST` (ou as credenciais oficiais do GCP) precisam ser configuradas no servidor.

## Classes Principais

- `GcsConfig`: Inicializa e configura o bean do cliente `Storage`, apontando para a URL do emulador ou da nuvem através da propriedade `gcs.host`.
- `FileStorageService`: Contém a regra de negócio do upload. Gera os UUIDs, converte os tipos MIME e envia os bytes ao bucket (padrão `webchat-files`). Utiliza a propriedade `gcs.public-url` para retornar a URL acessível de fora da rede interna do Docker.
- `FileController`: Expõe a API REST protegida para o Frontend.

## API REST

### `POST /api/v1/files/upload`
Upload de arquivo anexado pelo usuário.
- **Autorização:** Bearer Token (Qualquer papel)
- **Body:** `multipart/form-data` (campo `file`)
- **Resposta (200 OK):**
```json
{
  "url": "http://localhost:4443/storage/v1/b/webchat-files/o/123e4567-e89b-12d3-a456-426614174000.png?alt=media"
}
```

## Tratamento de URLs (Docker x Host)

Devido ao isolamento de redes do Docker, o Spring Boot (no backend) precisa utilizar um hostname interno para alcançar o serviço de storage, enquanto o navegador do usuário precisa de uma URL externa (`localhost`).
Para isso, o `FileStorageService` usa duas propriedades distintas:
- `gcs.host` (`http://gcs:4443`): Para upload de arquivos, visível só para o Backend.
- `gcs.public-url` (`http://localhost:4443`): URL salva no banco e acessada pelo Frontend para renderização visual.
