#!/usr/bin/env bash
set -euo pipefail

CONTEXTO="${KUBE_CONTEXT:-kind-autocenter}"
NAMESPACE="${NAMESPACE:-autocenter}"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

kc() { kubectl --context "$CONTEXTO" "$@"; }

kubectl config get-contexts -o name | grep -qx "$CONTEXTO" \
  || { echo "ERRO: contexto $CONTEXTO nao existe. Suba o cluster de autocenter-k8s-infra." >&2; exit 1; }

kc -n "$NAMESPACE" get secret os-service-secrets >/dev/null 2>&1 \
  || { echo "ERRO: Secret os-service-secrets nao existe. Crie-o antes (docs/contratos/README.md)." >&2; exit 1; }

kc apply -k "$RAIZ/k8s/banco"
kc -n "$NAMESPACE" rollout status deploy/os-mysql --timeout=300s

PV="$(kc -n "$NAMESPACE" get pvc os-mysql-pvc -o jsonpath='{.spec.volumeName}')"
[[ -n "$PV" ]] || { echo "ERRO: PVC os-mysql-pvc ainda sem volume." >&2; exit 1; }
kc patch pv "$PV" -p '{"spec":{"persistentVolumeReclaimPolicy":"Retain"}}'
echo "Banco pronto. Volume $PV com reclaimPolicy=$(kc get pv "$PV" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')."
