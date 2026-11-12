#!/usr/bin/env bash
set -euo pipefail

CONTEXTO="${KUBE_CONTEXT:-kind-autocenter}"
NAMESPACE="${NAMESPACE:-autocenter}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXEMPLOS="$DIR/../docs/contratos/exemplos"

uso() {
  echo "Uso: $0 <tipo> <osId>" >&2
  echo "Tipos: $(cd "$EXEMPLOS" && ls [0-9][0-9]-billing-*.json [0-9][0-9]-execucao-*.json \
    | sed -E 's/^[0-9]{2}-(billing|execucao)-//; s/\.json$//' | tr '\n' ' ')" >&2
}

[ $# -eq 2 ] || { uso; exit 2; }
TIPO="$1"
OS_ID="$2"

command -v jq >/dev/null || { echo "ERRO: jq nao encontrado" >&2; exit 1; }
[[ "$OS_ID" =~ ^[1-9][0-9]*$ ]] || { echo "ERRO: osId deve ser inteiro positivo, sem zeros a esquerda: $OS_ID" >&2; exit 2; }

ARQUIVO=""
if [[ "$TIPO" =~ ^[a-z]+\.[a-z-]+$ ]]; then
  ARQUIVO="$(compgen -G "$EXEMPLOS/[0-9][0-9]-billing-$TIPO.json" || compgen -G "$EXEMPLOS/[0-9][0-9]-execucao-$TIPO.json" || true)"
fi
if [ -z "$ARQUIVO" ] || [ ! -f "$ARQUIVO" ]; then
  echo "ERRO: tipo desconhecido: $TIPO" >&2; uso; exit 2
fi

validar_sobrescrita() {
  local valor="${!1:-}"
  [ -n "$valor" ] || return 0
  [[ "$valor" =~ ^[1-9][0-9]*$ ]] || { echo "ERRO: $1 deve ser inteiro positivo: $valor" >&2; exit 2; }
  jq -e --arg c "$2" '.payload | has($c)' < "$ARQUIVO" >/dev/null \
    || { echo "ERRO: $TIPO nao tem payload.$2; $1 nao se aplica" >&2; exit 2; }
}
validar_sobrescrita TENTATIVA tentativa
validar_sobrescrita PAGAMENTO_ID pagamentoId

case "$TIPO" in
  orcamento.*|pagamento.*) TOPICO="billing.eventos" ;;
  diagnostico.*|execucao.*) TOPICO="execucao.eventos" ;;
  *) echo "ERRO: tipo sem topico mapeado: $TIPO" >&2; exit 2 ;;
esac

EVENT_ID="$(cat /proc/sys/kernel/random/uuid)"
CORRELATION_ID="${CORRELATION_ID:-$(cat /proc/sys/kernel/random/uuid)}"
OCORRIDO_EM="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

MENSAGEM="$(jq -c --arg e "$EVENT_ID" --arg c "$CORRELATION_ID" --arg t "$OCORRIDO_EM" --argjson o "$OS_ID" \
  --arg tentativa "${TENTATIVA:-}" --arg pagamento "${PAGAMENTO_ID:-}" \
  '.eventId=$e | .correlationId=$c | .ocorridoEm=$t | .osId=$o
   | if $tentativa != "" then .payload.tentativa=($tentativa|tonumber) else . end
   | if $pagamento != "" then .payload.pagamentoId=($pagamento|tonumber) else . end' < "$ARQUIVO")"

printf '%s|%s\n' "$OS_ID" "$MENSAGEM" | kubectl --context "$CONTEXTO" -n "$NAMESPACE" exec -i kafka-0 -- \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$TOPICO" \
  --property parse.key=true --property key.separator='|'

echo "Publicado $TIPO (osId=$OS_ID, eventId=$EVENT_ID) em $TOPICO"
