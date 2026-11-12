#!/usr/bin/env bash
set -euo pipefail

CONTEXTO="${KUBE_CONTEXT:-kind-autocenter}"
NAMESPACE="${NAMESPACE:-autocenter}"
TIPO="${1:-interno}"
CLIENTE_ID="${2:-1}"
VALIDADE="${VALIDADE_SEGUNDOS:-3600}"

[[ "$TIPO" == "interno" || "$TIPO" == "cliente" ]] || { echo "ERRO: tipo deve ser interno ou cliente" >&2; exit 2; }
[[ "$CLIENTE_ID" =~ ^[0-9]+$ ]] || { echo "ERRO: clienteId deve ser inteiro" >&2; exit 2; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

SEGREDO="$(kubectl --context "$CONTEXTO" -n "$NAMESPACE" get secret os-service-secrets \
  -o jsonpath='{.data.SISTEMA_SEGURANCA_CHAVE_SECRETA}' | base64 -d)"
[ -n "$SEGREDO" ] || { echo "ERRO: segredo vazio ou Secret inexistente" >&2; exit 1; }

AGORA="$(date +%s)"
EXP=$((AGORA + VALIDADE))
CABECALHO='{"alg":"HS256","typ":"JWT"}'
if [ "$TIPO" = "cliente" ]; then
  CARGA="{\"iss\":\"Auto Center Fiap\",\"sub\":\"cliente-teste\",\"tipo\":\"cliente\",\"clienteId\":$CLIENTE_ID,\"iat\":$AGORA,\"exp\":$EXP}"
else
  CARGA="{\"iss\":\"Auto Center Fiap\",\"sub\":\"admin-teste\",\"tipo\":\"interno\",\"iat\":$AGORA,\"exp\":$EXP}"
fi

PARTES="$(printf '%s' "$CABECALHO" | b64url).$(printf '%s' "$CARGA" | b64url)"
ASSINATURA="$(printf '%s' "$PARTES" | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$(printf '%s' "$SEGREDO" | od -An -tx1 | tr -d ' \n')" -binary | b64url)"
printf '%s.%s\n' "$PARTES" "$ASSINATURA"
