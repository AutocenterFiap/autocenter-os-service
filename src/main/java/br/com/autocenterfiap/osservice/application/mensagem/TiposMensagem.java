package br.com.autocenterfiap.osservice.application.mensagem;

public final class TiposMensagem {

    public static final String OS_CRIADA = "os.criada";
    public static final String OS_CANCELADA = "os.cancelada";
    public static final String OS_CONCLUIDA = "os.concluida";
    public static final String OS_ENTREGUE = "os.entregue";
    public static final String BILLING_GERAR_ORCAMENTO = "billing.gerar-orcamento";
    public static final String BILLING_GERAR_COBRANCA = "billing.gerar-cobranca";
    public static final String BILLING_ESTORNAR_PAGAMENTO = "billing.estornar-pagamento";
    public static final String EXECUCAO_INICIAR_REPARO = "execucao.iniciar-reparo";

    public static final String ORCAMENTO_GERADO = "orcamento.gerado";
    public static final String ORCAMENTO_FALHOU = "orcamento.falhou";
    public static final String ORCAMENTO_APROVADO = "orcamento.aprovado";
    public static final String ORCAMENTO_REPROVADO = "orcamento.reprovado";
    public static final String PAGAMENTO_LINK_GERADO = "pagamento.link-gerado";
    public static final String PAGAMENTO_CONFIRMADO = "pagamento.confirmado";
    public static final String PAGAMENTO_RECUSADO = "pagamento.recusado";
    public static final String PAGAMENTO_ESTORNADO = "pagamento.estornado";
    public static final String PAGAMENTO_ESTORNO_FALHOU = "pagamento.estorno-falhou";

    public static final String DIAGNOSTICO_INICIADO = "diagnostico.iniciado";
    public static final String DIAGNOSTICO_CONCLUIDO = "diagnostico.concluido";
    public static final String EXECUCAO_INICIADA = "execucao.iniciada";
    public static final String EXECUCAO_FINALIZADA = "execucao.finalizada";
    public static final String EXECUCAO_FALHOU = "execucao.falhou";

    private TiposMensagem() {
    }
}
