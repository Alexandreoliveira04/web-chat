"""Trava de custo do Web Chat.

O orcamento do projeto publica, varias vezes por dia, o gasto do mes num topico Pub/Sub.
Esta funcao le esse aviso e, se o gasto passar do valor do orcamento, desvincula o
faturamento do projeto. Sem faturamento, todos os servicos param e nada mais e cobrado.
Para voltar, basta religar o faturamento no console.
"""

import base64
import json
import os

import functions_framework
from google.cloud import billing_v1

PROJECT_NAME = f"projects/{os.environ['PROJECT_ID']}"


def passou_do_orcamento(aviso: dict) -> bool:
    return aviso.get("costAmount", 0) > aviso.get("budgetAmount", 0)


@functions_framework.cloud_event
def stop_billing(cloud_event):
    aviso = json.loads(base64.b64decode(cloud_event.data["message"]["data"]).decode("utf-8"))
    gasto, limite = aviso.get("costAmount", 0), aviso.get("budgetAmount", 0)

    if not passou_do_orcamento(aviso):
        print(f"Dentro do orcamento: gasto {gasto} de {limite}. Nada a fazer.")
        return

    client = billing_v1.CloudBillingClient()
    if not client.get_project_billing_info(name=PROJECT_NAME).billing_enabled:
        print("Faturamento ja estava desligado.")
        return

    client.update_project_billing_info(
        name=PROJECT_NAME,
        project_billing_info=billing_v1.ProjectBillingInfo(billing_account_name=""),
    )
    print(f"Gasto {gasto} passou do limite {limite}: faturamento desligado em {PROJECT_NAME}.")
