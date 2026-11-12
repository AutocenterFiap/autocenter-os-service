package br.com.autocenterfiap.osservice.domain.enums;

public enum StatusOS {
    ABERTA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO, PAGAMENTO_RECUSADO,
    EM_EXECUCAO, AGUARDANDO_ESTORNO, CONCLUIDA, ENTREGUE, ORCAMENTO_REPROVADO, CANCELADA;

    public boolean isFinal() {
        return this == ENTREGUE || this == ORCAMENTO_REPROVADO || this == CANCELADA;
    }

    public boolean bloqueiaNovaOS() {
        return ordinal() <= EM_EXECUCAO.ordinal();
    }

    public boolean podeCancelar() {
        return ordinal() <= PAGAMENTO_RECUSADO.ordinal();
    }
}
