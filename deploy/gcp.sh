#!/usr/bin/env bash
# Deploy do Web Chat no Google Cloud dentro do nivel gratuito:
#   Cloud Run (desliga quando ninguem usa) + Cloud Storage + banco PostgreSQL no Neon (gratis).
#
# Uso:
#   PROJECT_ID=meu-projeto DATABASE_URL_FILE=~/neon-url.txt ICE_SERVERS_FILE=~/turn.txt ./deploy/gcp.sh
#
# DATABASE_URL_FILE aponta para um arquivo com a connection string do Neon
# (postgresql://usuario:senha@host/banco?sslmode=require). Tambem da para passar a string
# direto em DATABASE_URL.
# ICE_SERVERS_FILE (opcional) aponta para a lista JSON de servidores STUN/TURN das chamadas
# de voz, como a que o painel da Metered mostra. Sem ela, vale a ultima lista ja enviada; se
# nunca houve uma, as chamadas usam so STUN e podem nao conectar entre redes diferentes.
# Pode ser rodado de novo: so publica uma nova versao do codigo. Detalhes em docs/deploy.md.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?Defina PROJECT_ID, ex.: PROJECT_ID=meu-projeto ./deploy/gcp.sh}"
# O Cloud Storage so e gratis nas regioes dos EUA; o Cloud Run fica na mesma regiao.
REGION="${REGION:-us-central1}"
SERVICE="${SERVICE:-webchat}"
BUCKET="${BUCKET:-${PROJECT_ID}-webchat-files}"
RUN_SA_NAME="${RUN_SA_NAME:-webchat-run}"
BUDGET_NAME="${BUDGET_NAME:-webchat-custo-zero}"
# Rede de seguranca do projeto inteiro, na moeda da conta de faturamento. O limite de gastos
# nativo do Google (configurado no console, ver docs/deploy.md) so cobre o Cloud Run.
BUDGET_AMOUNT="${BUDGET_AMOUNT:-6}"
BUDGET_TOPIC="${BUDGET_TOPIC:-webchat-orcamento}"
GUARD_SA_NAME="${GUARD_SA_NAME:-webchat-billing-guard}"
GUARD_FUNCTION="${GUARD_FUNCTION:-webchat-billing-guard}"

if [[ -z "${DATABASE_URL:-}" && -n "${DATABASE_URL_FILE:-}" ]]; then
	DATABASE_URL="$(tr -d '[:space:]' < "${DATABASE_URL_FILE/#\~/$HOME}")"
fi
DATABASE_URL="${DATABASE_URL:?Defina DATABASE_URL_FILE (ou DATABASE_URL) com a connection string do Neon}"

# postgresql://usuario:senha@host/banco?parametros -> URL JDBC, usuario e senha separados.
if [[ ! "$DATABASE_URL" =~ ^postgres(ql)?://([^:]+):([^@]+)@([^/?]+)/([^?]+)(\?(.*))?$ ]]; then
	echo "Connection string invalida: esperado postgresql://usuario:senha@host/banco" >&2
	exit 1
fi
DB_USER="${BASH_REMATCH[2]}"
DB_PASSWORD="${BASH_REMATCH[3]}"
DB_HOST="${BASH_REMATCH[4]/-pooler./.}"   # conexao direta: o Flyway nao gosta do pooler
DB_NAME="${BASH_REMATCH[5]}"
JDBC_URL="jdbc:postgresql://${DB_HOST}/${DB_NAME}?sslmode=require"
if [[ "${BASH_REMATCH[7]:-}" == *"channel_binding=require"* ]]; then
	JDBC_URL="${JDBC_URL}&channelBinding=require"
fi

cd "$(dirname "$0")/.."
gcloud config set project "$PROJECT_ID" --quiet >/dev/null

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
RUN_SA="${RUN_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
GUARD_SA="${GUARD_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
# A URL do Cloud Run segue um formato fixo, entao da para liberar a origem antes do deploy.
APP_URL="https://${SERVICE}-${PROJECT_NUMBER}.${REGION}.run.app"

step() { printf '\n==> %s\n' "$*"; }

step "Ativando as APIs necessarias"
# Sem faturamento ativo, esta etapa ja falha (o Cloud Run exige faturamento mesmo no gratuito).
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
	secretmanager.googleapis.com storage.googleapis.com iam.googleapis.com \
	billingbudgets.googleapis.com cloudbilling.googleapis.com pubsub.googleapis.com \
	cloudfunctions.googleapis.com eventarc.googleapis.com --quiet

# As APIs de faturamento contam requisicoes na cota de quem chama. Sem isto, o gcloud usa uma
# cota compartilhada por todos os usuarios da ferramenta, que as vezes estoura (erro 429).
gbilling() { gcloud --billing-project="$PROJECT_ID" billing "$@"; }

BILLING_ACCOUNT="$(gbilling projects describe "$PROJECT_ID" --format='value(billingAccountName)')"
if [[ -z "$BILLING_ACCOUNT" ]]; then
	echo "O projeto $PROJECT_ID nao tem faturamento ativo. O Cloud Run exige, mesmo no nivel gratuito." >&2
	exit 1
fi
BILLING_ACCOUNT="${BILLING_ACCOUNT#billingAccounts/}"

# Em projetos novos, o build do "--source" roda com a conta padrao do Compute Engine,
# que precisa do papel de builder do Cloud Run.
BUILD_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
if gcloud iam service-accounts describe "$BUILD_SA" >/dev/null 2>&1; then
	gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$BUILD_SA" \
		--role=roles/run.builder --condition=None --quiet >/dev/null
fi

step "Trava de custo: se o gasto do mes passar de ${BUDGET_AMOUNT}, o faturamento e desligado"
# O orcamento publica o gasto do mes no topico; a funcao desvincula o faturamento do projeto
# quando o gasto passa do limite (ver deploy/billing-guard/main.py). Sem faturamento, tudo
# para e nada mais e cobrado. Os dados de custo chegam com algumas horas de atraso.
if ! gcloud pubsub topics describe "$BUDGET_TOPIC" >/dev/null 2>&1; then
	gcloud pubsub topics create "$BUDGET_TOPIC" --quiet
fi
if ! gcloud iam service-accounts describe "$GUARD_SA" >/dev/null 2>&1; then
	gcloud iam service-accounts create "$GUARD_SA_NAME" --display-name="Web Chat (trava de custo)" --quiet
fi
# Desvincular o faturamento do projeto (billing.projectManager) e receber os avisos do topico
# pelo gatilho do Eventarc (run.invoker, eventarc.eventReceiver e serviceUsageConsumer, que o
# Eventarc exige da conta usada no gatilho).
for role in roles/billing.projectManager roles/run.invoker roles/eventarc.eventReceiver \
		roles/serviceusage.serviceUsageConsumer; do
	gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$GUARD_SA" \
		--role="$role" --condition=None --quiet >/dev/null
done
gcloud functions deploy "$GUARD_FUNCTION" --gen2 --runtime=python312 --region="$REGION" --quiet \
	--source=deploy/billing-guard --entry-point=stop_billing \
	--trigger-topic="$BUDGET_TOPIC" --service-account="$GUARD_SA" --trigger-service-account="$GUARD_SA" \
	--memory=256Mi --max-instances=1 --set-env-vars="PROJECT_ID=$PROJECT_ID"

BUDGET_ID="$(gbilling budgets list --billing-account="$BILLING_ACCOUNT" \
	--filter="displayName=$BUDGET_NAME" --format='value(name)' | sed 's#.*/##')"
TOPIC_PATH="projects/$PROJECT_ID/topics/$BUDGET_TOPIC"
if [[ -z "$BUDGET_ID" ]]; then
	CURRENCY="$(gbilling accounts describe "$BILLING_ACCOUNT" --format='value(currencyCode)' 2>/dev/null || true)"
	# Os e-mails de alerta continuam indo para os administradores do faturamento.
	gbilling budgets create --billing-account="$BILLING_ACCOUNT" --display-name="$BUDGET_NAME" \
		--budget-amount="${BUDGET_AMOUNT}${CURRENCY:-USD}" --filter-projects="projects/$PROJECT_ID" \
		--threshold-rule=percent=0.01 --threshold-rule=percent=0.5 --threshold-rule=percent=1.0 \
		--notifications-rule-pubsub-topic="$TOPIC_PATH" --quiet
else
	CURRENCY="$(gbilling budgets describe "$BUDGET_ID" --billing-account="$BILLING_ACCOUNT" \
		--format='value(amount.specifiedAmount.currencyCode)')"
	gbilling budgets update "$BUDGET_ID" --billing-account="$BILLING_ACCOUNT" \
		--budget-amount="${BUDGET_AMOUNT}${CURRENCY:-USD}" \
		--notifications-rule-pubsub-topic="$TOPIC_PATH" --quiet >/dev/null
fi

step "Segredos: chave do JWT (gerada uma vez), senha do banco e servidores TURN"
if ! gcloud secrets describe webchat-jwt-secret >/dev/null 2>&1; then
	openssl rand -base64 48 | tr -d '\n' | gcloud secrets create webchat-jwt-secret --data-file=- \
		--replication-policy=automatic --quiet
fi
# Cria o segredo ou adiciona uma versao nova so quando o valor mudou.
upsert_secret() {
	local name="$1" value="$2"
	if ! gcloud secrets describe "$name" >/dev/null 2>&1; then
		printf '%s' "$value" | gcloud secrets create "$name" --data-file=- --replication-policy=automatic --quiet
	elif [[ "$(gcloud secrets versions access latest --secret="$name")" != "$value" ]]; then
		printf '%s' "$value" | gcloud secrets versions add "$name" --data-file=- --quiet
	fi
}
upsert_secret webchat-db-password "$DB_PASSWORD"
unset DB_PASSWORD DATABASE_URL

# Servidores STUN/TURN das chamadas (credenciais do TURN: o repositorio e publico).
if [[ -n "${ICE_SERVERS_FILE:-}" ]]; then
	ICE_SERVERS_JSON="$(python3 -I -c 'import json,sys
d=json.load(open(sys.argv[1]))
assert isinstance(d,list) and d and all("urls" in s for s in d), "precisa ser uma lista de servidores com urls"
print(json.dumps(d,separators=(",",":")))' "${ICE_SERVERS_FILE/#\~/$HOME}")" \
		|| { echo "ICE_SERVERS_FILE invalido: esperado a lista JSON iceServers" >&2; exit 1; }
	upsert_secret webchat-ice-servers "$ICE_SERVERS_JSON"
	unset ICE_SERVERS_JSON
fi
APP_SECRETS="JWT_SECRET=webchat-jwt-secret:latest,DB_PASSWORD=webchat-db-password:latest"
RUN_SECRETS=(webchat-jwt-secret webchat-db-password)
if gcloud secrets describe webchat-ice-servers >/dev/null 2>&1; then
	APP_SECRETS="${APP_SECRETS},ICE_SERVERS=webchat-ice-servers:latest"
	RUN_SECRETS+=(webchat-ice-servers)
fi

step "Arquivos: bucket $BUCKET no Cloud Storage, com leitura publica"
if ! gcloud storage buckets describe "gs://$BUCKET" >/dev/null 2>&1; then
	gcloud storage buckets create "gs://$BUCKET" --location="$REGION" --uniform-bucket-level-access --quiet
fi
# As URLs dos arquivos vao direto para o navegador, sem token: os objetos precisam ser publicos.
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" \
	--member=allUsers --role=roles/storage.objectViewer --quiet >/dev/null

step "Conta de servico do Cloud Run, so com as permissoes necessarias"
if ! gcloud iam service-accounts describe "$RUN_SA" >/dev/null 2>&1; then
	gcloud iam service-accounts create "$RUN_SA_NAME" --display-name="Web Chat (Cloud Run)" --quiet
fi
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" --member="serviceAccount:$RUN_SA" \
	--role=roles/storage.objectAdmin --quiet >/dev/null
for secret in "${RUN_SECRETS[@]}"; do
	gcloud secrets add-iam-policy-binding "$secret" --member="serviceAccount:$RUN_SA" \
		--role=roles/secretmanager.secretAccessor --quiet >/dev/null
done

step "Build da imagem (na nuvem, pelo Dockerfile) e deploy no Cloud Run"
# min 0: desliga quando ninguem usa, para ficar no nivel gratuito (o primeiro acesso demora mais).
# max 1: o broker STOMP, a presenca e as chamadas ficam na memoria de um processo so.
# timeout 3600: cada conexao WebSocket pode durar ate 60 minutos (o frontend reconecta).
gcloud run deploy "$SERVICE" --source . --region="$REGION" --quiet \
	--service-account="$RUN_SA" \
	--allow-unauthenticated \
	--port=8080 --memory=1Gi --cpu=1 --cpu-boost \
	--min-instances=0 --max-instances=1 \
	--session-affinity --timeout=3600 \
	--set-env-vars="^@^SPRING_DATASOURCE_URL=${JDBC_URL}@DB_USER=${DB_USER}@GCS_HOST=https://storage.googleapis.com@GCS_PUBLICURL=https://storage.googleapis.com@GCS_BUCKET=${BUCKET}@GCS_PROJECTID=${PROJECT_ID}@GCS_INITBUCKET=false@WS_ALLOWED_ORIGINS=${APP_URL}@CORS_ALLOWED_ORIGINS=${APP_URL}" \
	--set-secrets="$APP_SECRETS"

step "Limpeza das imagens antigas (o Artifact Registry so e gratis ate 0,5 GB)"
POLICY_FILE="$(mktemp)"
cat > "$POLICY_FILE" <<'JSON'
[
  { "name": "manter-as-2-mais-recentes", "action": { "type": "Keep" }, "mostRecentVersions": { "keepCount": 2 } },
  { "name": "apagar-as-outras", "action": { "type": "Delete" }, "condition": { "tagState": "any" } }
]
JSON
# cloud-run-source-deploy guarda as imagens do app; gcf-artifacts, as da trava de custo.
for repo in cloud-run-source-deploy gcf-artifacts; do
	gcloud artifacts repositories set-cleanup-policies "$repo" --location="$REGION" \
		--policy="$POLICY_FILE" --no-dry-run --quiet >/dev/null \
		|| echo "Aviso: nao foi possivel configurar a limpeza das imagens antigas em $repo."
done
rm -f "$POLICY_FILE"

step "Pronto"
echo "Aplicacao: $APP_URL"
echo "Saude:     $APP_URL/api/v1/health"
echo "Trava:     faturamento desligado automaticamente se o gasto do mes passar de ${BUDGET_AMOUNT}"
