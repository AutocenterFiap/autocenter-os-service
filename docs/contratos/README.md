# Contrato de mensagens e simulador de eventos

Em `exemplos/` ha um JSON por
tipo publicado por Billing e Execucao (14 tipos). Os valores de `eventId`, `osId`,
`correlationId` e `ocorridoEm` dos exemplos sao placeholders: o script os substitui.

## Deploy no cluster kind `autocenter`

Todos os comandos usam `--context kind-autocenter`: a maquina tem outro contexto padrao
(EKS real) e ele nunca deve ser usado aqui.

### 1. Secret (nunca versionado, valores aleatorios locais)
`k8s/secret.exemplo.yaml` so mostra as chaves e nao entra no `kustomization`. Crie o Secret real:
```bash
kubectl --context kind-autocenter -n autocenter create secret generic os-service-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=DB_USERNAME=osservice \
  --from-literal=DB_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=SISTEMA_SEGURANCA_CHAVE_SECRETA="$(openssl rand -hex 32)"
```
As credenciais do MySQL (`os-mysql`) e o segredo do JWT vem todos desse Secret.

### 2. Imagem e aplicacao
```bash
docker build -f docker/Dockerfile -t os-service:local .
kind load docker-image os-service:local --name autocenter
kubectl --context kind-autocenter apply -k k8s
kubectl --context kind-autocenter -n autocenter rollout status deploy/os-service --timeout=180s
```
**Nova imagem com a mesma tag:** `apply` nao muda o Deployment, entao depois de um novo
`kind load` rode `kubectl --context kind-autocenter -n autocenter rollout restart deploy/os-service`.

### Decisoes
- **Datadog:** o agente do cluster ainda nao existe. O tracer fica na imagem e e carregado
  (`-javaagent` em `JAVA_OPTS`), mas `DD_TRACE_ENABLED=false` e
  `DD_INSTRUMENTATION_TELEMETRY_ENABLED=false` (em `k8s/configmap.yaml`) o mantem inerte, sem erros nem
  logs de conexao. Quando o agente estiver no ar, troque `DD_TRACE_ENABLED` para `"true"` e
  faca `rollout restart`.
- **Memoria:** request 512Mi, limite 768Mi, heap em 60% (`MaxRAMPercentage`). Medido: ~360Mi em uso.
- **Ordem de subida:** o init container `wait-for-mysql` segura o pod ate o MySQL aceitar conexao;
  nao ha CrashLoop se o `os-mysql` subir depois.
- **`replicas: 1`:** o relay do outbox nao foi desenhado para varias instancias.
- Probes e `/actuator/prometheus` respondem sem token (liberados no `ConfiguracaoSeguranca`).
- O Ingress expoe so `/v1/ordem-servicos`; os endpoints `/actuator` nao saem do cluster.
- `cadastro.modo` continua `stub`.

## Simulador (Billing e Execucao)

```bash
T=$(scripts/gerar-token.sh)                       # interno; "cliente <id>" gera token de cliente
curl -s -X POST localhost/v1/ordem-servicos -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -d '{"clienteId":1,"veiculoId":1,"problemaRelatado":"Barulho no motor"}'
scripts/publicar-evento.sh diagnostico.iniciado 1   # <tipo> <osId>
curl -s localhost/v1/ordem-servicos/status/1 -H "Authorization: Bearer $T"   # EM_DIAGNOSTICO
```
`publicar-evento.sh` publica via `kubectl exec kafka-0`, com chave Kafka = `osId`, no topico
certo (`billing.eventos` para `orcamento.*` e `pagamento.*`; `execucao.eventos` para
`diagnostico.*` e `execucao.*`), e injeta `eventId` (UUID novo) e `ocorridoEm` (UTC). Use
`CORRELATION_ID=<uuid>` para manter o mesmo `correlationId` da OS. Tipo desconhecido sai com codigo 2.

Para publicar o mesmo `eventId` de novo (testar idempotencia) publique manualmente com
`kafka-console-producer.sh`; o script sempre gera um id novo.

## docker-compose
`docker/docker-compose.yml` sobe MySQL e Kafka KRaft e o servico `kafka-topicos`, que cria os 5
topicos e 2 DLTs com 3 particoes (a DLT publica na mesma particao da mensagem original; com 1
particao as particoes 1 e 2 falhariam). Ele usa `localhost:9092` e `localhost:3306`.
