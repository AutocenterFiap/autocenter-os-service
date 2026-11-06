# Setup do GitHub (passos manuais)

Nada aqui foi executado ainda: o pipeline (`.github/workflows/ci-cd.yml`) nunca rodou no GitHub.

## 1. Repositorios na organizacao `AutocenterFiap`

Crie `autocenter-os-service` e `autocenter-k8s-infra`.

**Recomendacao de seguranca:** use repositorios **privados**. O job `deploy` roda no seu computador (runner
self-hosted); em repositorio publico, qualquer pessoa poderia propor codigo que executaria na sua maquina. Se precisar
ser publico: em *Settings > Actions > General > Fork pull request workflows*, marque *Require approval for all outside
collaborators*, e nao deixe o runner atender workflows de fork. Os jobs de PR (`build-test`, `sonar`) rodam so em
`ubuntu-latest`; so o `deploy` usa o runner e ele so dispara em push para `main`.

## 2. Primeiro push (por PR)

```bash
git remote add origin git@github.com:AutocenterFiap/autocenter-os-service.git
git push -u origin feat/os-service
```
No GitHub, abra um PR de `feat/os-service` para `main` (a `main` precisa existir; se o repositorio estiver vazio, faca
antes o push de uma `main` inicial). O PR dispara `build-test` e `sonar`.

## 3. SonarCloud

1. Em sonarcloud.io, entre com o GitHub, importe a organizacao `AutocenterFiap` (chave `autocenterfiap`) e crie o
   projeto `autocenter-os-service` (chave `AutocenterFiap_autocenter-os-service`). Se a chave gerada for outra, ajuste
   `pom.xml`, `sonar-project.properties` e o `ci-cd.yml`.
2. Em *My Account > Security*, gere um token.
3. No repositorio: *Settings > Secrets and variables > Actions > New repository secret*, nome `SONAR_TOKEN`.
4. Desligue a *Automatic Analysis* do projeto (ela conflita com a analise do CI).

## 4. Protecao da `main`

*Settings > Branches > Add rule* para `main`: exigir PR antes do merge e *Require status checks to pass*. Os nomes dos
checks sao exatamente os nomes dos jobs do workflow:

- `build-test`
- `sonar`

(Os checks so aparecem na lista depois de rodarem uma vez, ou seja, depois do primeiro PR.) O `deploy` nao entra: ele so
roda depois do merge. Observacao: em PR de fork o job `sonar` e pulado (sem segredo) e um check pulado conta como aprovado;
por isso exija tambem revisao de PR.

## 5. Runner self-hosted

No computador que tem o cluster kind: *Settings > Actions > Runners > New self-hosted runner > Linux* e siga os comandos
mostrados (baixar, `./config.sh --url ... --token ...`, `./run.sh` ou `sudo ./svc.sh install && sudo ./svc.sh start`).
O usuario do runner precisa de: Docker (grupo `docker`), `kind`, `kubectl` e acesso ao cluster `autocenter`
(`kubectl config get-contexts` deve listar `kind-autocenter`). O `kustomize` avulso nao e necessario: o workflow usa o
kustomize embutido no `kubectl`.

**ATENCAO, contexto da AWS.** O contexto padrao do `kubectl` desta maquina e um EKS REAL na AWS. Por isso o workflow:

1. falha logo no inicio se o contexto `kind-autocenter` nao existir;
2. usa `--context kind-autocenter` em **todo** `kubectl`;
3. nunca executa `kubectl config use-context`.

Se editar o workflow, mantenha essas tres regras. Um `kubectl apply` sem `--context` aplicaria na AWS.

Pre-requisitos no cluster (o workflow nao os cria): o cluster de `autocenter-k8s-infra` no ar e o Secret
`os-service-secrets` criado a mao (ver `docs/contratos/README.md`).

## 6. Confirmar que o merge dispara o deploy

1. Faca o merge do PR em `main`.
2. Em *Actions*, abra a execucao do push: `build-test` e `sonar` passam e `deploy` roda no seu runner.
3. No terminal:
```bash
kubectl --context kind-autocenter -n autocenter get deploy os-service \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```
A imagem deve ser `os-service:<sha do commit do merge>`. A tag `local` nos manifestos so vale para uso manual.

O deploy nao e commitado: o workflow troca a tag numa copia temporaria de `k8s/`. O grupo de `concurrency` serializa
deploys (sem cancelar o que esta em andamento), porque o `Recreate` nao pode se sobrepor.
