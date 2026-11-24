#!/usr/bin/env bash
# Configura, uma unica vez, o deploy automatico pelo GitHub Actions (.github/workflows/deploy.yml).
#
# Uso:  PROJECT_ID=meu-projeto ./deploy/github-actions.sh
#
# Cria uma Workload Identity Federation: o Google passa a aceitar os tokens OIDC que o GitHub
# Actions emite, mas so os deste repositorio e so da branch main, e os troca por uma
# credencial temporaria da conta de servico webchat-deployer. Nenhuma chave fica guardada no
# GitHub. Pode ser rodado de novo sem problema.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?Defina PROJECT_ID, ex.: PROJECT_ID=meu-projeto ./deploy/github-actions.sh}"
REPOSITORY="${REPOSITORY:-Alexandreoliveira04/web-chat}"
BRANCH="${BRANCH:-main}"
POOL="${POOL:-github}"
PROVIDER="${PROVIDER:-web-chat}"
DEPLOYER_NAME="${DEPLOYER_NAME:-webchat-deployer}"
RUN_SA_NAME="${RUN_SA_NAME:-webchat-run}"

gcloud config set project "$PROJECT_ID" --quiet >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
DEPLOYER="${DEPLOYER_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
RUN_SA="${RUN_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
BUILD_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

step() { printf '\n==> %s\n' "$*"; }

step "Ativando as APIs da troca de tokens"
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com --quiet

step "Conta de servico $DEPLOYER_NAME, usada so pelo deploy automatico"
if ! gcloud iam service-accounts describe "$DEPLOYER" >/dev/null 2>&1; then
	gcloud iam service-accounts create "$DEPLOYER_NAME" --display-name="Web Chat (deploy pelo GitHub Actions)" --quiet
fi
# Publicar a partir do codigo-fonte (build no Cloud Build e nova revisao no Cloud Run).
for role in roles/run.sourceDeveloper roles/serviceusage.serviceUsageConsumer; do
	gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$DEPLOYER" \
		--role="$role" --condition=None --quiet >/dev/null
done
# "Agir como" so as duas contas envolvidas: a do app (Cloud Run) e a do build.
for sa in "$RUN_SA" "$BUILD_SA"; do
	gcloud iam service-accounts add-iam-policy-binding "$sa" --member="serviceAccount:$DEPLOYER" \
		--role=roles/iam.serviceAccountUser --quiet >/dev/null
done

step "Workload Identity Federation: so $REPOSITORY, branch $BRANCH"
if ! gcloud iam workload-identity-pools describe "$POOL" --location=global >/dev/null 2>&1; then
	gcloud iam workload-identity-pools create "$POOL" --location=global \
		--display-name="GitHub Actions" --quiet
fi
CONDITION="assertion.repository=='${REPOSITORY}' && assertion.ref=='refs/heads/${BRANCH}'"
if ! gcloud iam workload-identity-pools providers describe "$PROVIDER" --location=global \
		--workload-identity-pool="$POOL" >/dev/null 2>&1; then
	gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" --location=global \
		--workload-identity-pool="$POOL" --display-name="$REPOSITORY" \
		--issuer-uri="https://token.actions.githubusercontent.com" \
		--attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
		--attribute-condition="$CONDITION" --quiet
else
	gcloud iam workload-identity-pools providers update-oidc "$PROVIDER" --location=global \
		--workload-identity-pool="$POOL" --attribute-condition="$CONDITION" --quiet >/dev/null
fi
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER" --role=roles/iam.workloadIdentityUser \
	--member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/attribute.repository/${REPOSITORY}" \
	--quiet >/dev/null

step "Pronto. Valores usados em .github/workflows/deploy.yml:"
echo "WORKLOAD_IDENTITY_PROVIDER: projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/providers/${PROVIDER}"
echo "DEPLOYER_SERVICE_ACCOUNT:   ${DEPLOYER}"
