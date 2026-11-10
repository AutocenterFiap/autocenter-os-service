# Contrato de mensagens e simulador de eventos

Em `exemplos/` ha um JSON por tipo de
mensagem (22 tipos: 8 do OS Service, 9 do Billing, 5 da Execucao), numerados na ordem do fluxo da Saga
como `<ordem>-<emissor>-<tipo>.json`. O indice, com topico e consumidor de cada um, esta em
[`exemplos/README.md`](exemplos/README.md). Os valores de `eventId`, `osId`, `correlationId` e
`ocorridoEm` dos exemplos sao placeholders: o script os substitui. O script so publica os exemplos de
emissor `billing` e `execucao`.

## Deploy no cluster kind `autocenter`

Todos os comandos usam `--context kind-autocenter`: a maquina tem outro contexto padrao
(EKS real) e ele nunca deve ser usado aqui.

### 1. Secret (nunca versionado, valores aleatorios locais)
`k8s/secret.exemplo.yaml` so mostra as chaves e nao entra em nenhum `kustomization`. Crie o Secret real:
```bash
kubectl --context kind-autocenter -n autocenter create secret generic os-service-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=DB_USERNAME=osservice \
  --from-literal=DB_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=SISTEMA_SEGURANCA_CHAVE_SECRETA="$(openssl rand -hex 32)"
```
As credenciais do MySQL (`os-mysql`) e o segredo do JWT vem todos desse Secret.

**Atencao, segredo do JWT:** a secao 7 da spec exige o **mesmo** segredo HS256 do monolito. O comando acima gera um
aleatorio, que so aceita tokens de `scripts/gerar-token.sh`: tokens reais do login do monolito dao 401 ate os dois
segredos coincidirem. Na integracao, use em `SISTEMA_SEGURANCA_CHAVE_SECRETA` o valor configurado no monolito.

### 2. Banco (uma vez)
```bash
./scripts/criar-banco.sh
```
Aplica `k8s/banco` (MySQL `os-mysql` com PVC de 2Gi), espera o rollout e marca o PV como `Retain`. E idempotente:
rodar de novo com o manifesto inalterado nao reinicia o banco. O banco tem ciclo de vida proprio:

- o deploy (pipeline ou `apply -k k8s/app`) nunca o aplica, entao nao o reinicia, recria nem apaga;
- `kubectl delete -k k8s/app` remove so a aplicacao;
- com `Retain`, ate apagar o PVC por engano preserva os dados no disco do no (o PV fica `Released`). Para religar:
  `kubectl --context kind-autocenter patch pv <pv> --type=json -p '[{"op":"remove","path":"/spec/claimRef"}]'`, e
  recrie o PVC com `spec.volumeName: <pv>` antes de rodar o script;
- o que apaga os dados e `kind delete cluster`, porque o volume vive dentro do container do no.

### 3. Imagem e aplicacao
```bash
docker build -f docker/Dockerfile -t os-service:local .
kind load docker-image os-service:local --name autocenter
kubectl --context kind-autocenter apply -k k8s/app
kubectl --context kind-autocenter -n autocenter rollout status deploy/os-service --timeout=180s
```
**Nova imagem com a mesma tag:** `apply` nao muda o Deployment, entao depois de um novo
`kind load` rode `kubectl --context kind-autocenter -n autocenter rollout restart deploy/os-service`.

### Decisoes
- **Datadog:** o agente do cluster ainda nao existe. O tracer fica na imagem e e carregado
  (`-javaagent` em `JAVA_OPTS`), mas `DD_TRACE_ENABLED=false` e
  `DD_INSTRUMENTATION_TELEMETRY_ENABLED=false` (em `k8s/app/configmap.yaml`) o mantem inerte, sem erros nem
  logs de conexao. Quando o agente estiver no ar, troque `DD_TRACE_ENABLED` para `"true"` e
  faca `rollout restart`.
- **Memoria:** request 512Mi, limite 768Mi, heap em 60% (`MaxRAMPercentage`). Medido: ~360Mi em uso.
- **Ordem de subida:** o init container `wait-for-mysql` segura o pod ate o MySQL aceitar conexao;
  nao ha CrashLoop se o `os-mysql` subir depois.
- **`replicas: 1`:** o relay do outbox nao foi desenhado para varias instancias.
- Probes e `/actuator/prometheus` respondem sem token (liberados no `ConfiguracaoSeguranca`).
- O Ingress expoe so `/v1/ordem-servicos`; os endpoints `/actuator` nao saem do cluster.
- `cadastro.modo` continua `stub`.
- **Prazo curto para demonstrar a expiracao:** use `SAGA_PAGAMENTO_VALIDADE_MINUTOS`, nao o perfil `demo`. O `ENTRYPOINT`
  da imagem passa `-Dspring.profiles.active=prod`, e propriedade de sistema vence `SPRING_PROFILES_ACTIVE`: o perfil
  `demo` nunca ativa em container. Para voltar ao padrao de 30 minutos, repita com `SAGA_PAGAMENTO_VALIDADE_MINUTOS-`:
  ```bash
  kubectl --context kind-autocenter -n autocenter set env deploy/os-service SAGA_PAGAMENTO_VALIDADE_MINUTOS=2
  ```
  Vale para cobrancas novas. O agendador roda a cada 30 s, entao o cancelamento vem ate 30 s depois do prazo. No
  docker-compose, exporte a mesma variavel antes de subir.

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
`diagnostico.*` e `execucao.*`), e injeta `eventId` (UUID novo) e `ocorridoEm` (UTC). Tipo desconhecido sai com
codigo 2. Variaveis opcionais:

| Variavel | Efeito |
|---|---|
| `CORRELATION_ID` | `correlationId` do envelope. O valor da OS vem do campo `correlationId` de `POST /v1/ordem-servicos` e `GET /v1/ordem-servicos/{id}`. Sem ela, o script gera um UUID novo. |
| `TENTATIVA` | Sobrescreve `payload.tentativa` (`pagamento.recusado`, `pagamento.link-gerado`). |
| `PAGAMENTO_ID` | Sobrescreve `payload.pagamentoId` (tipos `pagamento.*`). |

Os logs da OS sempre saem com o `correlationId` gravado nela (MDC), inclusive os do REST e do agendador, entao ele
filtra a saga inteira: `kubectl --context kind-autocenter -n autocenter logs deploy/os-service | grep <correlationId>`.
Passar `CORRELATION_ID` faz o evento bater com o da OS tambem no envelope e nos logs anteriores ao processamento
(mensagem repetida, por exemplo).

### Tres recusas cancelam a OS
`pagamento.recusado` so vale com `tentativa` igual a tentativa atual da saga, e a cobranca seguinte so reabre o pagamento
depois de `pagamento.link-gerado`. A partir de uma OS recem-aberta:
```bash
ID=1   # id devolvido pelo POST
P="scripts/publicar-evento.sh"
$P diagnostico.iniciado $ID; $P diagnostico.concluido $ID; $P orcamento.gerado $ID; $P orcamento.aprovado $ID
$P pagamento.link-gerado $ID                                  # AGUARDANDO_PAGAMENTO, tentativa 1
TENTATIVA=1 $P pagamento.recusado $ID                         # PAGAMENTO_RECUSADO, novo billing.gerar-cobranca
TENTATIVA=2 PAGAMENTO_ID=9002 $P pagamento.link-gerado $ID    # AGUARDANDO_PAGAMENTO
TENTATIVA=2 PAGAMENTO_ID=9002 $P pagamento.recusado $ID       # PAGAMENTO_RECUSADO
TENTATIVA=3 PAGAMENTO_ID=9003 $P pagamento.link-gerado $ID    # AGUARDANDO_PAGAMENTO
TENTATIVA=3 PAGAMENTO_ID=9003 $P pagamento.recusado $ID       # CANCELADA (PAGAMENTO_RECUSADO_LIMITE)
```

### Estorno
`pagamento.estornado` e `pagamento.estorno-falhou` entram no historico (`GET /{id}/historico`) mesmo sem mudar o status.
`GET /{id}` e `GET /numero/{numero}` trazem `estornoPendente` (`true` enquanto ha estorno a confirmar; so nessas rotas,
pois a lista e a abertura nao leem a saga).

### Cuidado: `diagnostico.concluido` repetido
Repetir `diagnostico.concluido` com **novo** `eventId` enquanto a OS esta em `EM_DIAGNOSTICO` nao e idempotente: a OS
troca os itens pelos da nova mensagem e sai um segundo `billing.gerar-orcamento`. Como o simulador sempre gera um id
novo, rodar o script duas vezes tem esse efeito.

Para publicar o mesmo `eventId` de novo (testar idempotencia) publique manualmente com
`kafka-console-producer.sh`; o script sempre gera um id novo.

## docker-compose
`docker/docker-compose.yml` sobe MySQL, Kafka KRaft, o servico `kafka-topicos`, que cria os 5
topicos e 2 DLTs com 3 particoes (a DLT publica na mesma particao da mensagem original; com 1
particao as particoes 1 e 2 falhariam), e o `os-service`. No host, usa `localhost:3306`,
`localhost:9092` e `localhost:8098`; entre containers, o Kafka responde em `kafka:29092`.

`SISTEMA_SEGURANCA_CHAVE_SECRETA` e obrigatoria, sem valor versionado: exporte no shell ou
grave em `docker/.env` (ignorado pelo git). Para rodar o servico pelo Maven, suba so a infra:
`docker compose -f docker/docker-compose.yml up -d mysql kafka kafka-topicos`.
`scripts/publicar-evento.sh` e `scripts/gerar-token.sh` falam com o cluster kind, nao com o compose.
