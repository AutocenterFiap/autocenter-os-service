#!/usr/bin/env bash
set -euo pipefail

CONTEXTO="${KUBE_CONTEXT:-kind-autocenter}"
NAMESPACE="${NAMESPACE:-autocenter}"
PORTA="${PORTA:-3307}"
REDE="${REDE:-autocenter}"
NO="${NO:-autocenter-control-plane}"
PROXY="autocenter-mysql-proxy"
IMAGEM="alpine/socat:1.8.0.3"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "${1:-}" == "--remover" ]]; then
  docker rm -f "$PROXY" >/dev/null 2>&1 && echo "Proxy $PROXY removido." || echo "Proxy $PROXY nao existia."
  exit 0
fi

docker inspect "$NO" >/dev/null 2>&1 || { echo "ERRO: no $NO nao existe. Suba o cluster antes." >&2; exit 1; }

kubectl --context "$CONTEXTO" apply -f "$RAIZ/k8s/banco/mysql-externo.yaml"

docker rm -f "$PROXY" >/dev/null 2>&1 || true
if ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${PORTA}$"; then
  echo "ERRO: porta ${PORTA} do host em uso. Use outra: PORTA=3308 $0" >&2
  exit 1
fi

SUBREDE="$(docker network inspect -f '{{range .IPAM.Config}}{{println .Subnet}}{{end}}' "$REDE" | grep -m1 '\.')"
case "${SUBREDE#*/}" in
  16) IP_PROXY="$(echo "$SUBREDE" | cut -d. -f1-2).255.250" ;;
  24) IP_PROXY="$(echo "$SUBREDE" | cut -d. -f1-3).250" ;;
  *) echo "ERRO: sub-rede IPv4 da rede $REDE nao suportada: ${SUBREDE:-nenhuma}" >&2; exit 1 ;;
esac

docker run -d --name "$PROXY" --restart unless-stopped --network "$REDE" --ip "$IP_PROXY" \
  -p "127.0.0.1:${PORTA}:3306" "$IMAGEM" \
  tcp-listen:3306,fork,reuseaddr "tcp-connect:${NO}:30306" >/dev/null

USUARIO="$(kubectl --context "$CONTEXTO" -n "$NAMESPACE" get secret os-service-secrets \
  -o jsonpath='{.data.DB_USERNAME}' | base64 -d)"
echo "MySQL em 127.0.0.1:${PORTA} (database os_service, usuario ${USUARIO})."
echo "Senha: kubectl --context $CONTEXTO -n $NAMESPACE get secret os-service-secrets -o jsonpath='{.data.DB_PASSWORD}' | base64 -d"
echo "No DBeaver: driver properties allowPublicKeyRetrieval=true e useSSL=false."
