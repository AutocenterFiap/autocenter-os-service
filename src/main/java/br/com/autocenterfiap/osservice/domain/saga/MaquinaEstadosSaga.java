package br.com.autocenterfiap.osservice.domain.saga;

import static br.com.autocenterfiap.osservice.domain.enums.StatusOS.*;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.util.List;
import java.util.Objects;

public final class MaquinaEstadosSaga {

    private MaquinaEstadosSaga() {
    }

    public static Decisao decidir(EstadoSaga estado, Gatilho gatilho) {
        StatusOS status = estado.status();
        return switch (gatilho.tipo()) {
            case DIAGNOSTICO_INICIADO -> status == ABERTA
                ? Decisao.aceitar(List.of(EM_DIAGNOSTICO), List.of()) : Decisao.ignorar();
            case DIAGNOSTICO_CONCLUIDO -> diagnosticoConcluido(status, gatilho);
            case ORCAMENTO_GERADO -> status == EM_DIAGNOSTICO
                ? Decisao.aceitar(List.of(AGUARDANDO_APROVACAO), List.of()) : Decisao.ignorar();
            case ORCAMENTO_FALHOU -> status == EM_DIAGNOSTICO
                ? Decisao.cancelar(List.of(CANCELADA), "ORCAMENTO_FALHOU") : Decisao.ignorar();
            case ORCAMENTO_APROVADO -> status == AGUARDANDO_APROVACAO
                ? Decisao.aceitar(List.of(AGUARDANDO_PAGAMENTO), List.of(Acao.ENVIAR_GERAR_COBRANCA))
                : Decisao.ignorar();
            case ORCAMENTO_REPROVADO -> status == AGUARDANDO_APROVACAO
                ? Decisao.cancelar(List.of(StatusOS.ORCAMENTO_REPROVADO), "ORCAMENTO_REPROVADO")
                : Decisao.ignorar();
            case PAGAMENTO_LINK_GERADO -> linkGerado(status);
            case PAGAMENTO_CONFIRMADO -> confirmado(estado, gatilho);
            case PAGAMENTO_RECUSADO -> recusado(estado, gatilho);
            case PAGAMENTO_ESTORNADO -> estornado(estado, gatilho);
            case PAGAMENTO_ESTORNO_FALHOU -> Decisao.aceitar(List.of(), List.of(Acao.ALERTAR_ESTORNO_FALHOU));
            case EXECUCAO_INICIADA -> status == EM_EXECUCAO
                ? Decisao.aceitar(List.of(), List.of()) : Decisao.ignorar();
            case EXECUCAO_FINALIZADA -> status == EM_EXECUCAO
                ? Decisao.aceitar(List.of(CONCLUIDA), List.of(Acao.PUBLICAR_OS_CONCLUIDA)) : Decisao.ignorar();
            case EXECUCAO_FALHOU -> status == EM_EXECUCAO
                ? Decisao.estornar(List.of(AGUARDANDO_ESTORNO), estado.pagamentoConfirmadoId(), "EXECUCAO_FALHOU", true)
                : Decisao.ignorar();
            case PRAZO_VENCIDO -> status == AGUARDANDO_PAGAMENTO || status == PAGAMENTO_RECUSADO
                ? Decisao.cancelar(List.of(CANCELADA), "LINK_EXPIRADO") : Decisao.ignorar();
            case ENTREGA_SOLICITADA -> status == CONCLUIDA
                ? Decisao.aceitar(List.of(ENTREGUE), List.of(Acao.PUBLICAR_OS_ENTREGUE)) : Decisao.ignorar();
            case CANCELAMENTO_SOLICITADO -> status.podeCancelar()
                ? Decisao.cancelar(List.of(CANCELADA), "CANCELAMENTO_SOLICITADO") : Decisao.ignorar();
            case REPROCESSAR_ESTORNO -> estado.estornoPendenteId() != null
                ? Decisao.estornar(List.of(), estado.estornoPendenteId(), "REPROCESSAMENTO_MANUAL", false)
                : Decisao.ignorar();
        };
    }

    private static Decisao diagnosticoConcluido(StatusOS status, Gatilho gatilho) {
        if (status != EM_DIAGNOSTICO) {
            return Decisao.ignorar();
        }
        return gatilho.semItens()
            ? Decisao.cancelar(List.of(CANCELADA), "DIAGNOSTICO_SEM_ITENS")
            : Decisao.aceitar(List.of(), List.of(Acao.ENVIAR_GERAR_ORCAMENTO));
    }

    private static Decisao linkGerado(StatusOS status) {
        return switch (status) {
            case AGUARDANDO_PAGAMENTO -> Decisao.aceitar(List.of(), List.of());
            case PAGAMENTO_RECUSADO -> Decisao.aceitar(List.of(AGUARDANDO_PAGAMENTO), List.of());
            default -> Decisao.ignorar();
        };
    }

    private static Decisao confirmado(EstadoSaga estado, Gatilho gatilho) {
        if (Objects.equals(gatilho.pagamentoId(), estado.pagamentoConfirmadoId())
                || Objects.equals(gatilho.pagamentoId(), estado.estornoPendenteId())) {
            return Decisao.ignorar();
        }
        boolean semPendente = estado.estornoPendenteId() == null;
        return switch (estado.status()) {
            case AGUARDANDO_PAGAMENTO, PAGAMENTO_RECUSADO ->
                Decisao.aceitar(List.of(EM_EXECUCAO), List.of(Acao.ENVIAR_INICIAR_REPARO));
            case CANCELADA -> Decisao.estornar(List.of(), gatilho.pagamentoId(), "PAGAMENTO_ATRASADO", semPendente);
            case EM_EXECUCAO, AGUARDANDO_ESTORNO, CONCLUIDA, ENTREGUE ->
                Decisao.estornar(List.of(), gatilho.pagamentoId(), "PAGAMENTO_DUPLICADO", semPendente);
            default -> Decisao.ignorar();
        };
    }

    private static Decisao recusado(EstadoSaga estado, Gatilho gatilho) {
        if (estado.status() != AGUARDANDO_PAGAMENTO
                || !Objects.equals(gatilho.tentativa(), estado.tentativasPagamento())) {
            return Decisao.ignorar();
        }
        if (estado.tentativasPagamento() >= estado.maxTentativas()) {
            return Decisao.cancelar(List.of(StatusOS.PAGAMENTO_RECUSADO, CANCELADA), "PAGAMENTO_RECUSADO_LIMITE");
        }
        return Decisao.aceitar(List.of(StatusOS.PAGAMENTO_RECUSADO), List.of(Acao.ENVIAR_GERAR_COBRANCA));
    }

    private static Decisao estornado(EstadoSaga estado, Gatilho gatilho) {
        if (gatilho.pagamentoId() == null
                || !Objects.equals(gatilho.pagamentoId(), estado.estornoPendenteId())) {
            return Decisao.ignorar();
        }
        if (estado.status() == AGUARDANDO_ESTORNO) {
            return new Decisao(true, List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), null,
                "EXECUCAO_FALHOU", true, false);
        }
        return new Decisao(true, List.of(), List.of(), null, null, true, false);
    }
}
