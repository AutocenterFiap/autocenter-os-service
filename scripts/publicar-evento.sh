#!/usr/bin/env bash
set -euo pipefail

CONTEXTO="${KUBE_CONTEXT:-kind-autocenter}"
NAMESPACE="${NAMESPACE:-autocenter}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXEMPLOS="$DIR/../docs/contratos/exemplos"

uso() { echo "Uso: $0 <tipo> <osId>" >&2; echo "Tipos: $(cd "$EXEMPLOS" && ls *.json | sed 's/\.json$//' | tr '\n' ' ')" >&2; }

[ $# -eq 2 ] || { uso; exit 2; }
TIPO="$1"
OS_ID="$2"

command -v jq >/dev/null || { echo "ERRO: jq nao encontrado" >&2; exit 1; }
[[ "$OS_ID" =~ ^[1-9][0-9]*$ ]] || { echo "ERRO: osId deve ser inteiro positivo, sem zeros a esquerda: $OS_ID" >&2; exit 2; }

ARQUIVO="$EXEMPLOS/$TIPO.json"
if [[ ! "$TIPO" =~ ^[a-z]+\.[a-z-]+$ ]] || [ ! -f "$ARQUIVO" ]; then
  echo "ERRO: tipo desconhecido: $TIPO" >&2; uso; exit 2
fi

case "$TIPO" in
  orcamento.*|pagamento.*) TOPICO="billing.eventos" ;;
  diagnostico.*|execucao.*) TOPICO="execucao.eventos" ;;
  *) echo "ERRO: tipo sem topico mapeado: $TIPO" >&2; exit 2 ;;
esac

EVENT_ID="$(cat /proc/sys/kernel/random/uuid)"
CORRELATION_ID="${CORRELATION_ID:-$(cat /proc/sys/kernel/random/uuid)}"
OCORRIDO_EM="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

MENSAGEM="$(jq -c --arg e "$EVENT_ID" --arg c "$CORRELATION_ID" --arg t "$OCORRIDO_EM" --argjson o "$OS_ID" \
  '.eventId=$e | .correlationId=$c | .ocorridoEm=$t | .osId=$o' < "$ARQUIVO")"

printf '%s|%s\n' "$OS_ID" "$MENSAGEM" | kubectl --context "$CONTEXTO" -n "$NAMESPACE" exec -i kafka-0 -- \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$TOPICO" \
  --property parse.key=true --property key.separator='|'

echo "Publicado $TIPO (osId=$OS_ID, eventId=$EVENT_ID) em $TOPICO"
