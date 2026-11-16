#!/usr/bin/env bash
set -euo pipefail

CLUSTER="${1:?Uso: $0 <cluster-kind> <tag-da-imagem>}"
TAG="${2:?Uso: $0 <cluster-kind> <tag-da-imagem>}"
CTX="kind-${CLUSTER}"
IMAGEM=os-service
NAMESPACE=autocenter
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
K="kubectl --context ${CTX}"

if ! kubectl config get-contexts -o name | grep -qx "${CTX}"; then
  echo "::error::Contexto ${CTX} nao existe nesta maquina. Abortando antes de qualquer acao."
  exit 1
fi

if ! ${K} -n "${NAMESPACE}" get secret os-service-secrets >/dev/null 2>&1; then
  echo "::error::Secret os-service-secrets nao existe em ${CTX}/${NAMESPACE}. Crie-o antes (docs/contratos/README.md)."
  exit 1
fi

docker build -f "${RAIZ}/docker/Dockerfile" -t "${IMAGEM}:${TAG}" "${RAIZ}"
kind load docker-image "${IMAGEM}:${TAG}" --name "${CLUSTER}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
cp -r "${RAIZ}/k8s" "${TMP}/k8s"
printf '\nimages:\n  - name: %s\n    newTag: "%s"\n' "${IMAGEM}" "${TAG}" >> "${TMP}/k8s/kustomization.yaml"
${K} apply -k "${TMP}/k8s"
${K} -n "${NAMESPACE}" rollout status "deployment/${IMAGEM}" --timeout=300s
