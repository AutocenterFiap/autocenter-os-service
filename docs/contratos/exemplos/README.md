# Exemplos de mensagens da Saga da OS

Um JSON por tipo de mensagem, numerado na ordem em que acontece no fluxo da OS.
Nome dos arquivos: `<ordem>-<emissor>-<tipo>.json`.

Todas as mensagens usam o mesmo envelope, e a chave Kafka e o `osId`:

```json
{
  "eventId": "uuid unico por mensagem (use para descartar repetidas)",
  "tipo": "os.criada",
  "versao": 1,
  "osId": 123,
  "correlationId": "uuid gerado na abertura da OS, igual em toda a Saga",
  "ocorridoEm": "2026-11-05T14:30:00Z",
  "payload": {}
}
```

Nos arquivos, `eventId`, `osId`, `correlationId` e `ocorridoEm` sao placeholders. Valores em dinheiro sao
strings com 2 casas (`"165.00"`). Todo consumidor deve ignorar campos desconhecidos.

## 1. Caminho feliz

| # | Quem publica | O que acontece | Tipo | Topico | Quem consome |
|---|---|---|---|---|---|
| 01 | OS Service | Cria a OS | `os.criada` | `os.eventos` | Execucao |
| 02 | Execucao | Inicia o diagnostico | `diagnostico.iniciado` | `execucao.eventos` | OS Service |
| 03 | Execucao | Conclui o diagnostico, com itens e precos | `diagnostico.concluido` | `execucao.eventos` | OS Service |
| 04 | OS Service | Pede o orcamento | `billing.gerar-orcamento` | `billing.comandos` | Billing |
| 05 | Billing | Gera o orcamento | `orcamento.gerado` | `billing.eventos` | OS Service |
| 06 | Billing | Cliente aprova o orcamento | `orcamento.aprovado` | `billing.eventos` | OS Service |
| 07 | OS Service | Pede a cobranca | `billing.gerar-cobranca` | `billing.comandos` | Billing |
| 08 | Billing | Gera o link de pagamento | `pagamento.link-gerado` | `billing.eventos` | OS Service |
| 09 | Billing | Confirma o pagamento | `pagamento.confirmado` | `billing.eventos` | OS Service |
| 10 | OS Service | Pede o reparo | `execucao.iniciar-reparo` | `execucao.comandos` | Execucao |
| 11 | Execucao | Inicia o reparo | `execucao.iniciada` | `execucao.eventos` | OS Service |
| 12 | Execucao | Finaliza o reparo | `execucao.finalizada` | `execucao.eventos` | OS Service |
| 13 | OS Service | Conclui a OS | `os.concluida` | `os.eventos` | informativo |
| 14 | OS Service | Entrega o veiculo (`POST /{id}/entregar`) | `os.entregue` | `os.eventos` | informativo |

## 2. Falhas e compensacoes

| # | Quem publica | O que acontece | Tipo | Topico | Quem consome | Reacao do OS Service |
|---|---|---|---|---|---|---|
| 15 | Billing | Falha ao gerar orcamento (no lugar do 05) | `orcamento.falhou` | `billing.eventos` | OS Service | Cancela a OS (22) |
| 16 | Billing | Cliente reprova o orcamento (no lugar do 06) | `orcamento.reprovado` | `billing.eventos` | OS Service | Cancela a OS (22) |
| 17 | Billing | Pagamento recusado (no lugar do 09) | `pagamento.recusado` | `billing.eventos` | OS Service | Nova cobranca (07, `tentativa` + 1); na 3a recusa cancela a OS (22) |
| 18 | Execucao | Reparo falhou (no lugar do 12) | `execucao.falhou` | `execucao.eventos` | OS Service | Pede estorno (19) |
| 19 | OS Service | Pede o estorno | `billing.estornar-pagamento` | `billing.comandos` | Billing | — |
| 20 | Billing | Estorno feito | `pagamento.estornado` | `billing.eventos` | OS Service | Cancela a OS (22) |
| 21 | Billing | Estorno falhou | `pagamento.estorno-falhou` | `billing.eventos` | OS Service | Alerta; reenvio manual por `POST /{id}/reprocessar-estorno` |
| 22 | OS Service | Cancela a OS | `os.cancelada` | `os.eventos` | Execucao, Billing | — |

Tambem levam a `os.cancelada` (22): diagnostico concluido sem itens (03 com listas vazias), link de
pagamento expirado e `POST /{id}/cancelar` antes da execucao. Um `pagamento.confirmado` que chega com a OS
ja cancelada ou ja paga gera estorno (19).

Valores de `motivo`:

- `os.cancelada`: `DIAGNOSTICO_SEM_ITENS`, `ORCAMENTO_FALHOU`, `ORCAMENTO_REPROVADO`,
  `PAGAMENTO_RECUSADO_LIMITE`, `LINK_EXPIRADO`, `CANCELAMENTO_SOLICITADO`, `EXECUCAO_FALHOU`
- `billing.estornar-pagamento`: `EXECUCAO_FALHOU`, `PAGAMENTO_ATRASADO`, `PAGAMENTO_DUPLICADO`,
  `REPROCESSAMENTO_MANUAL`

## O que cada servico precisa implementar

- **Billing** consome 04, 07, 19 e 22 e publica 05, 06, 08, 09, 15, 16, 17, 20 e 21.
- **Execucao** consome 01, 10 e 22 e publica 02, 03, 11, 12 e 18.

Para simular Billing e Execucao no cluster, use `scripts/publicar-evento.sh <tipo> <osId>`
(ver [`../README.md`](../README.md)).
