# Deploy no Google Cloud (nível gratuito)

A aplicação roda sem custo, combinando o nível gratuito do Google Cloud com um banco
PostgreSQL gratuito no Neon:

| Peça | Serviço | Grátis até (por mês) |
| ---- | ------- | -------------------- |
| Aplicação (backend + frontend) | **Cloud Run** (`us-central1`) | ~50 horas com o app ativo (180 mil vCPU-segundos) e 2 milhões de requisições |
| Arquivos | **Cloud Storage** (`us-central1`) | 5 GB guardados e 1 GB de download |
| Banco | **Neon** (PostgreSQL, AWS US East 2) | 0,5 GB; o plano grátis não pede cartão |
| Build da imagem | Cloud Build + Artifact Registry | minutos de build de sobra; 0,5 GB de imagens |
| Segredos | Secret Manager | os 2 segredos usados |

Os limites dos planos gratuitos mudam com o tempo; confira nas páginas de preço do Google
Cloud e do Neon.

O script [`deploy/gcp.sh`](../deploy/gcp.sh) instala a trava de custo, cria o que falta e
publica a aplicação.

## Pré-requisitos

1. Projeto no Google Cloud com **faturamento ativo** (o Cloud Run exige, mesmo no nível
   gratuito). Anote o **ID do projeto**.
2. **`gcloud`** instalado e logado:

   ```bash
   sudo snap install google-cloud-cli --classic
   gcloud auth login
   ```

3. Projeto no **Neon** com só o Postgres ligado, na região **AWS US East 2 (Ohio)**, a mais
   próxima de `us-central1`. Salve a connection string (botão *Connect*) num arquivo fora do
   repositório, legível só por você:

   ```bash
   nano ~/neon-url.txt && chmod 600 ~/neon-url.txt
   ```

   Pode ser a string com ou sem pool de conexões: o script troca pelo endereço direto, que
   é o que o Flyway precisa.

## Publicar

Na raiz do projeto:

```bash
PROJECT_ID=meu-projeto DATABASE_URL_FILE=~/neon-url.txt ./deploy/gcp.sh
```

A primeira execução leva uns 10 minutos, quase tudo no build das imagens. No fim aparece a
URL da aplicação (`https://webchat-<número>.us-central1.run.app`). Para publicar uma nova
versão, rode o mesmo comando.

Variáveis opcionais: `REGION` (padrão `us-central1`), `SERVICE`, `BUCKET`, `BUDGET_AMOUNT`
(padrão `6`, na moeda da conta de faturamento).

## Trava de custo

O uso normal fica no nível gratuito. Para o caso de algo sair do previsto, há duas camadas,
pensadas para o gasto nunca passar de R$ 10:

| Camada | Cobre | Valor | O que faz |
| ------ | ----- | ----- | --------- |
| Limite de gastos nativo do Google (console) | só o **Cloud Run**, onde está quase todo o custo possível | R$ 3 | pausa o serviço do app |
| Trava do script (`webchat-custo-zero`) | **o projeto inteiro** (Cloud Storage, build, imagens...) | R$ 6 | desliga o faturamento do projeto |

**Limite de gastos nativo.** Em *Faturamento → Orçamentos e limites*, crie um orçamento
mensal para o projeto com o serviço **Cloud Run**, valor R$ 3, alertas em 50%, 80% e 100% e
o limite de gastos ativado. Hoje esse recurso só vale para um serviço por vez, e só para
Cloud Run, Cloud Run Functions, Gemini API e Vertex AI, por isso ele não substitui a trava
do script. Não marque "Todos os serviços": isso inclui o Cloud Run Functions, onde a trava
roda, e o limite poderia pausá-la. **Não use limite de R$ 0,00**: o primeiro centavo de
arredondamento já pausa os serviços, e eles só voltam no mês seguinte ou suspendendo o
limite à mão.

**Trava do script.** O Google Cloud não tem um teto de gastos para o projeto todo, então o
script instala uma, no padrão documentado pelo próprio Google:

```text
orçamento (gasto do mês) ──► tópico Pub/Sub ──► função webchat-billing-guard
                                                   │ gasto > limite?
                                                   ▼
                                     desvincula o faturamento do projeto
```

- O orçamento `webchat-custo-zero` publica o gasto do mês várias vezes por dia e manda
  e-mails aos administradores do faturamento ao chegar a 1%, 50% e 100% do limite.
- A função [`deploy/billing-guard/main.py`](../deploy/billing-guard/main.py) desliga o
  faturamento quando o gasto passa do limite (`BUDGET_AMOUNT`, padrão 6 na moeda da conta).
  Sem faturamento, todos os serviços param e nada mais é cobrado.
- A conta de serviço `webchat-billing-guard` só pode gerenciar o faturamento deste projeto.
- **Ressalva:** os dados de custo chegam com até 24 horas de atraso, então as duas camadas
  podem agir um pouco depois do valor exato. Os limites mais baixos que R$ 10 deixam essa
  margem.
- **Para voltar** depois de a trava disparar: religue o faturamento do projeto no console
  (Faturamento → Minhas contas → vincular o projeto) e rode o script de novo.

Também ajudam a ficar no gratuito:

- **No máximo 1 instância** do Cloud Run, e **zero quando ninguém usa**.
- **Abas em segundo plano desconectam** depois de 5 minutos (`app/chat/page.tsx`): uma
  conexão WebSocket aberta conta como uso no Cloud Run, e uma aba esquecida manteria o
  servidor ligado. Ao voltar para a aba, o app reconecta e recarrega as conversas.
- **Limpeza automática** das imagens antigas no Artifact Registry (mantém as 2 mais
  recentes), para não passar de 0,5 GB.

## O que o script cria

1. **APIs** do Cloud Run, Cloud Build, Artifact Registry, Secret Manager, Cloud Storage, IAM,
   orçamentos, faturamento, Pub/Sub, Cloud Functions e Eventarc.
2. **A trava de custo**: tópico `webchat-orcamento`, função `webchat-billing-guard` e o
   orçamento `webchat-custo-zero`.
3. **Segredos** no Secret Manager: `webchat-jwt-secret` (gerado uma vez) e
   `webchat-db-password` (a senha do Neon). Nada disso fica no código.
4. **O bucket** `<projeto>-webchat-files`, com leitura pública dos objetos: as URLs dos
   arquivos vão direto para o navegador, sem token.
5. **A conta de serviço** `webchat-run`, só com acesso ao bucket e aos dois segredos.
6. **O build e o serviço** `webchat` no Cloud Run.

## Configuração no Cloud Run

| Opção | Valor | Por quê |
| ----- | ----- | ------- |
| instâncias | mínimo 0, máximo 1 | desliga sem uso (gratuito); o broker STOMP, a presença e as chamadas vivem na memória de um processo só |
| `--cpu-boost` | ligado | mais CPU durante a partida, para o primeiro acesso demorar menos |
| `--session-affinity` | ligado | mantém cada navegador na mesma instância |
| `--timeout` | 3600 s | tempo máximo de uma conexão WebSocket no Cloud Run |
| memória / CPU | 1 GiB / 1 | suficiente para o Spring Boot; a JVM usa até 75% da memória (`-XX:MaxRAMPercentage=75` no `Dockerfile`) |
| `--allow-unauthenticated` | ligado | a página é pública; a autenticação é a do próprio app (JWT) |

Variáveis de ambiente:

| Variável | Valor |
| -------- | ----- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host do Neon>/neondb?sslmode=require&channelBinding=require` |
| `DB_USER` / `DB_PASSWORD` | usuário do Neon / segredo `webchat-db-password` |
| `JWT_SECRET` | segredo `webchat-jwt-secret` |
| `GCS_HOST`, `GCS_PUBLICURL` | `https://storage.googleapis.com` |
| `GCS_BUCKET`, `GCS_PROJECTID` | o bucket e o projeto |
| `GCS_INITBUCKET` | `false` (o bucket já foi criado pelo script) |
| `WS_ALLOWED_ORIGINS`, `CORS_ALLOWED_ORIGINS` | a URL do Cloud Run |

**Storage sem arquivo de chave.** No Cloud Run, o SDK do Cloud Storage usa a conta de serviço
automaticamente; o `GcsConfig` só troca o endereço do emulador pelo do Google (`GCS_HOST`).

**Sem administrador inicial.** O script não define `ADMIN_EMAIL`/`ADMIN_PASSWORD`. Para ter
um, acrescente as duas variáveis no serviço.

## No dia da avaliação

O servidor desliga sem uso, e o primeiro acesso depois disso leva de 10 a 20 segundos. Para
uma avaliação, deixe uma instância sempre ligada durante o período e volte depois:

```bash
gcloud run services update webchat --region=us-central1 --min-instances=1   # antes
gcloud run services update webchat --region=us-central1 --min-instances=0   # depois
```

Algumas horas ou um dia ligado ficam no nível gratuito ou custam centavos.

## Limitações

- **Partida a frio**: o primeiro acesso depois de um tempo sem uso leva uns 10 a 20 segundos,
  enquanto o Spring Boot sobe.
- **Estado em memória some quando o servidor desliga**: presença e chamadas em andamento. Só
  acontece quando ninguém está usando.
- **Abas em segundo plano** por mais de 5 minutos aparecem offline e não recebem chamadas até
  voltar para a aba.
- **Conexões WebSocket duram até 60 minutos** no Cloud Run. O frontend reconecta sozinho em
  5 segundos, mas uma chamada de voz mais longa que isso cai.
- **Uma instância só**: para escalar seria preciso um broker externo (ver
  [arquitetura](backend/architecture.md#limitações-conhecidas)).
- **Sem servidor TURN**: em redes muito restritivas, o áudio das chamadas pode não conectar
  (ver [call](backend/modules/call.md#limitações-conhecidas)).

## Apagar tudo

```bash
gcloud run services delete webchat --region=us-central1
gcloud functions delete webchat-billing-guard --region=us-central1
gcloud storage rm --recursive gs://<projeto>-webchat-files
```

Ou apague o projeto inteiro no console. O banco é apagado no painel do Neon.
