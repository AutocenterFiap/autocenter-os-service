# language: pt
Funcionalidade: Saga da Ordem de Servico
  O OS Service orquestra a Saga. Billing e Execucao sao simulados por um duble que
  consome os comandos reais do Kafka e publica os eventos reais de resposta.

  Cenário: Fluxo completo até a entrega
    Dado uma OS aberta pela API
    Então o OS Service publica o evento "os.criada"
    Quando a Execução inicia o diagnóstico
    E a Execução conclui o diagnóstico com um serviço e uma peça
    Então o Billing recebe o comando "billing.gerar-orcamento"
    Quando o Billing gera o orçamento
    Então a OS fica com status "AGUARDANDO_APROVACAO"
    Quando o cliente aprova o orçamento
    Então a OS fica com status "AGUARDANDO_PAGAMENTO"
    E o Billing recebe o comando "billing.gerar-cobranca"
    Quando o Billing gera o link de pagamento da tentativa 1
    E o pagamento da tentativa 1 é confirmado
    Então a OS fica com status "EM_EXECUCAO"
    E a Execução recebe o comando "execucao.iniciar-reparo"
    Quando a Execução inicia o reparo
    E a Execução finaliza o reparo
    Então a OS fica com status "CONCLUIDA"
    E o OS Service publica o evento "os.concluida"
    Quando a entrega é registrada pela API
    Então a OS fica com status "ENTREGUE"
    E o OS Service publica o evento "os.entregue"
    E o Billing não recebe o comando "billing.estornar-pagamento"

  Cenário: Pagamento recusado seguido de pagamento aprovado no novo link
    Dado uma OS com orçamento aprovado e aguardando pagamento
    Quando o Billing gera o link de pagamento da tentativa 1
    E o pagamento da tentativa 1 é recusado
    Então a OS fica com status "PAGAMENTO_RECUSADO"
    E o Billing recebe 2 comandos "billing.gerar-cobranca"
    Quando o Billing gera o link de pagamento da tentativa 2
    Então a OS fica com status "AGUARDANDO_PAGAMENTO"
    Quando o pagamento da tentativa 2 é confirmado
    Então a OS fica com status "EM_EXECUCAO"
    E a Execução recebe o comando "execucao.iniciar-reparo"
    E o Billing recebe 2 comandos "billing.gerar-cobranca"
    E o OS Service não publica o evento "os.cancelada"

  Cenário: Três recusas cancelam a OS
    Dado uma OS com orçamento aprovado e aguardando pagamento
    Quando o Billing gera o link de pagamento da tentativa 1
    E o pagamento da tentativa 1 é recusado
    E o Billing gera o link de pagamento da tentativa 2
    E o pagamento da tentativa 2 é recusado
    E o Billing gera o link de pagamento da tentativa 3
    E o pagamento da tentativa 3 é recusado
    Então a OS fica com status "CANCELADA"
    E o OS Service publica o evento "os.cancelada" com motivo "PAGAMENTO_RECUSADO_LIMITE"
    E o Billing recebe 3 comandos "billing.gerar-cobranca"
    E a Execução não recebe o comando "execucao.iniciar-reparo"

  Cenário: Link expirado cancela a OS
    Dado uma OS com orçamento aprovado e aguardando pagamento
    Quando o Billing gera o link de pagamento da tentativa 1
    E o prazo de pagamento vence
    Então a OS fica com status "CANCELADA"
    E o OS Service publica o evento "os.cancelada" com motivo "LINK_EXPIRADO"
    E a Execução não recebe o comando "execucao.iniciar-reparo"
    E o Billing não recebe o comando "billing.estornar-pagamento"

  Cenário: Orçamento reprovado encerra sem reparo
    Dado uma OS com orçamento gerado e aguardando aprovação
    Quando o cliente reprova o orçamento
    Então a OS fica com status "ORCAMENTO_REPROVADO"
    E o OS Service publica o evento "os.cancelada" com motivo "ORCAMENTO_REPROVADO"
    E a Execução não recebe o comando "execucao.iniciar-reparo"
    E o Billing não recebe o comando "billing.gerar-cobranca"

  Cenário: Falha na execução pede estorno e só cancela depois dele
    Dado uma OS com pagamento confirmado e em execução
    Quando a Execução informa falha no reparo
    Então a OS fica com status "AGUARDANDO_ESTORNO"
    E o Billing recebe o comando "billing.estornar-pagamento"
    E o OS Service não publica o evento "os.cancelada"
    Quando o Billing confirma o estorno
    Então a OS fica com status "CANCELADA"
    E o OS Service publica o evento "os.cancelada" com motivo "EXECUCAO_FALHOU"
    E o Billing recebe 1 comandos "billing.estornar-pagamento"

  Cenário: Pagamento atrasado em OS cancelada gera estorno
    Dado uma OS com orçamento aprovado e aguardando pagamento
    Quando o Billing gera o link de pagamento da tentativa 1
    E o cancelamento é solicitado pela API
    Então a OS fica com status "CANCELADA"
    E o Billing não recebe o comando "billing.estornar-pagamento"
    Quando o pagamento da tentativa 1 é confirmado
    Então o Billing recebe o comando "billing.estornar-pagamento"
    E a OS permanece com status "CANCELADA"
    E a Execução não recebe o comando "execucao.iniciar-reparo"
