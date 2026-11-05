package br.com.autocenterfiap.osservice.domain.saga;

import static br.com.autocenterfiap.osservice.domain.enums.StatusOS.*;
import static br.com.autocenterfiap.osservice.domain.saga.MaquinaEstadosSaga.decidir;
import static br.com.autocenterfiap.osservice.domain.saga.TipoGatilho.*;
import static br.com.autocenterfiap.osservice.domain.saga.TipoGatilho.ORCAMENTO_REPROVADO;
import static br.com.autocenterfiap.osservice.domain.saga.TipoGatilho.PAGAMENTO_RECUSADO;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class MaquinaEstadosSagaTest {

    private static EstadoSaga estado(StatusOS s, int tentativas) {
        return new EstadoSaga(s, tentativas, null, null, 3);
    }

    private static EstadoSaga estado(StatusOS s, int tentativas, Long confirmado, Long pendente) {
        return new EstadoSaga(s, tentativas, confirmado, pendente, 3);
    }

    private static Arguments linha(String nome, EstadoSaga e, Gatilho g, List<StatusOS> transicoes,
            List<Acao> acoes, String motivo) {
        return Arguments.of(nome, e, g, transicoes, acoes, motivo);
    }

    static Stream<Arguments> tabela() {
        return Stream.of(
            linha("ABERTA diagnostico.iniciado", estado(ABERTA, 0), Gatilho.de(DIAGNOSTICO_INICIADO),
                List.of(EM_DIAGNOSTICO), List.of(), null),
            linha("diagnostico.concluido com itens", estado(EM_DIAGNOSTICO, 0), Gatilho.diagnostico(false),
                List.of(), List.of(Acao.ENVIAR_GERAR_ORCAMENTO), null),
            linha("diagnostico.concluido sem itens", estado(EM_DIAGNOSTICO, 0), Gatilho.diagnostico(true),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "DIAGNOSTICO_SEM_ITENS"),
            linha("orcamento.gerado", estado(EM_DIAGNOSTICO, 0), Gatilho.de(ORCAMENTO_GERADO),
                List.of(AGUARDANDO_APROVACAO), List.of(), null),
            linha("orcamento.falhou", estado(EM_DIAGNOSTICO, 0), Gatilho.de(ORCAMENTO_FALHOU),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "ORCAMENTO_FALHOU"),
            linha("orcamento.aprovado", estado(AGUARDANDO_APROVACAO, 0), Gatilho.de(ORCAMENTO_APROVADO),
                List.of(AGUARDANDO_PAGAMENTO), List.of(Acao.ENVIAR_GERAR_COBRANCA), null),
            linha("orcamento.reprovado", estado(AGUARDANDO_APROVACAO, 0), Gatilho.de(ORCAMENTO_REPROVADO),
                List.of(StatusOS.ORCAMENTO_REPROVADO), List.of(Acao.PUBLICAR_OS_CANCELADA), "ORCAMENTO_REPROVADO"),
            linha("link-gerado aguardando pagamento", estado(AGUARDANDO_PAGAMENTO, 1),
                Gatilho.pagamento(PAGAMENTO_LINK_GERADO, 9L, 1, null), List.of(), List.of(), null),
            linha("link-gerado em recusado", estado(StatusOS.PAGAMENTO_RECUSADO, 1),
                Gatilho.pagamento(PAGAMENTO_LINK_GERADO, 9L, 2, null),
                List.of(AGUARDANDO_PAGAMENTO), List.of(), null),
            linha("confirmado aguardando pagamento", estado(AGUARDANDO_PAGAMENTO, 1),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(EM_EXECUCAO), List.of(Acao.ENVIAR_INICIAR_REPARO), null),
            linha("confirmado em recusado", estado(StatusOS.PAGAMENTO_RECUSADO, 1),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(EM_EXECUCAO), List.of(Acao.ENVIAR_INICIAR_REPARO), null),
            linha("recusado tentativa 1", estado(AGUARDANDO_PAGAMENTO, 1),
                Gatilho.pagamento(PAGAMENTO_RECUSADO, 9L, 1, "x"),
                List.of(StatusOS.PAGAMENTO_RECUSADO), List.of(Acao.ENVIAR_GERAR_COBRANCA), null),
            linha("recusado tentativa 2", estado(AGUARDANDO_PAGAMENTO, 2),
                Gatilho.pagamento(PAGAMENTO_RECUSADO, 9L, 2, "x"),
                List.of(StatusOS.PAGAMENTO_RECUSADO), List.of(Acao.ENVIAR_GERAR_COBRANCA), null),
            linha("recusado tentativa 3", estado(AGUARDANDO_PAGAMENTO, 3),
                Gatilho.pagamento(PAGAMENTO_RECUSADO, 9L, 3, "x"),
                List.of(StatusOS.PAGAMENTO_RECUSADO, CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA),
                "PAGAMENTO_RECUSADO_LIMITE"),
            linha("prazo vencido aguardando pagamento", estado(AGUARDANDO_PAGAMENTO, 1), Gatilho.de(PRAZO_VENCIDO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "LINK_EXPIRADO"),
            linha("prazo vencido em recusado", estado(StatusOS.PAGAMENTO_RECUSADO, 1), Gatilho.de(PRAZO_VENCIDO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "LINK_EXPIRADO"),
            linha("execucao.iniciada", estado(EM_EXECUCAO, 1, 9L, null), Gatilho.de(EXECUCAO_INICIADA),
                List.of(), List.of(), null),
            linha("execucao.finalizada", estado(EM_EXECUCAO, 1, 9L, null), Gatilho.de(EXECUCAO_FINALIZADA),
                List.of(CONCLUIDA), List.of(Acao.PUBLICAR_OS_CONCLUIDA), null),
            linha("execucao.falhou", estado(EM_EXECUCAO, 1, 9L, null), Gatilho.de(EXECUCAO_FALHOU),
                List.of(AGUARDANDO_ESTORNO), List.of(Acao.ENVIAR_ESTORNO), "EXECUCAO_FALHOU"),
            linha("estornado em aguardando estorno", estado(AGUARDANDO_ESTORNO, 1, 9L, 9L),
                Gatilho.pagamento(PAGAMENTO_ESTORNADO, 9L, null, null),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "EXECUCAO_FALHOU"),
            linha("estorno-falhou aguardando estorno", estado(AGUARDANDO_ESTORNO, 1, 9L, 9L),
                Gatilho.pagamento(PAGAMENTO_ESTORNO_FALHOU, 9L, null, "x"),
                List.of(), List.of(Acao.ALERTAR_ESTORNO_FALHOU), null),
            linha("reprocessar em aguardando estorno", estado(AGUARDANDO_ESTORNO, 1, 9L, 9L),
                Gatilho.de(REPROCESSAR_ESTORNO),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "REPROCESSAMENTO_MANUAL"),
            linha("reprocessar em cancelada", estado(CANCELADA, 1, 9L, 8L), Gatilho.de(REPROCESSAR_ESTORNO),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "REPROCESSAMENTO_MANUAL"),
            linha("confirmado atrasado em cancelada", estado(CANCELADA, 1),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "PAGAMENTO_ATRASADO"),
            linha("estornado em cancelada", estado(CANCELADA, 1, null, 9L),
                Gatilho.pagamento(PAGAMENTO_ESTORNADO, 9L, null, null), List.of(), List.of(), null),
            linha("estorno-falhou em cancelada", estado(CANCELADA, 1, null, 9L),
                Gatilho.pagamento(PAGAMENTO_ESTORNO_FALHOU, 9L, null, "x"),
                List.of(), List.of(Acao.ALERTAR_ESTORNO_FALHOU), null),
            linha("confirmado de outra cobranca em EM_EXECUCAO", estado(EM_EXECUCAO, 1, 8L, null),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "PAGAMENTO_DUPLICADO"),
            linha("confirmado de outra cobranca em AGUARDANDO_ESTORNO", estado(AGUARDANDO_ESTORNO, 1, 8L, 8L),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "PAGAMENTO_DUPLICADO"),
            linha("confirmado de outra cobranca em CONCLUIDA", estado(CONCLUIDA, 1, 8L, null),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "PAGAMENTO_DUPLICADO"),
            linha("confirmado de outra cobranca em ENTREGUE", estado(ENTREGUE, 1, 8L, null),
                Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null),
                List.of(), List.of(Acao.ENVIAR_ESTORNO), "PAGAMENTO_DUPLICADO"),
            linha("entregar", estado(CONCLUIDA, 1, 9L, null), Gatilho.de(ENTREGA_SOLICITADA),
                List.of(ENTREGUE), List.of(Acao.PUBLICAR_OS_ENTREGUE), null),
            linha("cancelar ABERTA", estado(ABERTA, 0), Gatilho.de(CANCELAMENTO_SOLICITADO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "CANCELAMENTO_SOLICITADO"),
            linha("cancelar EM_DIAGNOSTICO", estado(EM_DIAGNOSTICO, 0), Gatilho.de(CANCELAMENTO_SOLICITADO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "CANCELAMENTO_SOLICITADO"),
            linha("cancelar AGUARDANDO_APROVACAO", estado(AGUARDANDO_APROVACAO, 0),
                Gatilho.de(CANCELAMENTO_SOLICITADO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "CANCELAMENTO_SOLICITADO"),
            linha("cancelar AGUARDANDO_PAGAMENTO", estado(AGUARDANDO_PAGAMENTO, 1),
                Gatilho.de(CANCELAMENTO_SOLICITADO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "CANCELAMENTO_SOLICITADO"),
            linha("cancelar PAGAMENTO_RECUSADO", estado(StatusOS.PAGAMENTO_RECUSADO, 1),
                Gatilho.de(CANCELAMENTO_SOLICITADO),
                List.of(CANCELADA), List.of(Acao.PUBLICAR_OS_CANCELADA), "CANCELAMENTO_SOLICITADO"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tabela")
    void tabelaDeTransicoes(String nome, EstadoSaga estado, Gatilho gatilho, List<StatusOS> transicoes,
            List<Acao> acoes, String motivo) {
        var d = decidir(estado, gatilho);
        assertThat(d.aceita()).isTrue();
        assertThat(d.transicoes()).containsExactlyElementsOf(transicoes);
        assertThat(d.acoes()).containsExactlyElementsOf(acoes);
        assertThat(d.motivoCancelamento()).isEqualTo(motivo);
    }

    @Test
    void terceiraRecusaPassaPorRecusadoECancela() {
        var d = decidir(estado(AGUARDANDO_PAGAMENTO, 3), Gatilho.pagamento(PAGAMENTO_RECUSADO, 9L, 3, "x"));
        assertThat(d.transicoes()).containsExactly(StatusOS.PAGAMENTO_RECUSADO, StatusOS.CANCELADA);
        assertThat(d.acoes()).containsExactly(Acao.PUBLICAR_OS_CANCELADA);
    }

    @Test
    void recusaRepetidaDeTentativaAntigaEIgnorada() {
        assertThat(decidir(estado(AGUARDANDO_PAGAMENTO, 2),
            Gatilho.pagamento(PAGAMENTO_RECUSADO, 9L, 1, "x")).aceita()).isFalse();
    }

    @Test
    void confirmacaoRepetidaDoMesmoPagamentoEIgnorada() {
        var d = decidir(estado(EM_EXECUCAO, 1, 9L, null), Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null));
        assertThat(d.aceita()).isFalse();
        assertThat(d.acoes()).isEmpty();
    }

    @Test
    void confirmacaoEmRecusadoVale() {
        var d = decidir(estado(StatusOS.PAGAMENTO_RECUSADO, 1), Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null));
        assertThat(d.transicoes()).containsExactly(EM_EXECUCAO);
        assertThat(d.acoes()).containsExactly(Acao.ENVIAR_INICIAR_REPARO);
    }

    @Test
    void confirmacaoAtrasadaEmCanceladaEstorna() {
        var d = decidir(estado(CANCELADA, 1), Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null));
        assertThat(d.transicoes()).isEmpty();
        assertThat(d.acoes()).containsExactly(Acao.ENVIAR_ESTORNO);
        assertThat(d.pagamentoAEstornar()).isEqualTo(9L);
        assertThat(d.motivoCancelamento()).isEqualTo("PAGAMENTO_ATRASADO");
    }

    @Test
    void segundaConfirmacaoDeOutraCobrancaEstorna() {
        var d = decidir(estado(EM_EXECUCAO, 1, 8L, null), Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null));
        assertThat(d.acoes()).containsExactly(Acao.ENVIAR_ESTORNO);
        assertThat(d.pagamentoAEstornar()).isEqualTo(9L);
    }

    @Test
    void confirmacaoEmEstadoSemSentidoEIgnorada() {
        assertThat(decidir(estado(ABERTA, 0), Gatilho.pagamento(PAGAMENTO_CONFIRMADO, 9L, 1, null)).aceita())
            .isFalse();
    }

    @Test
    void execucaoFalhouEstornaOPagamentoConfirmado() {
        var d = decidir(estado(EM_EXECUCAO, 1, 9L, null), Gatilho.de(EXECUCAO_FALHOU));
        assertThat(d.transicoes()).containsExactly(AGUARDANDO_ESTORNO);
        assertThat(d.pagamentoAEstornar()).isEqualTo(9L);
    }

    @Test
    void estornoConfirmadoCancelaSomenteSeForOPendente() {
        var d = decidir(estado(AGUARDANDO_ESTORNO, 1, 9L, 9L),
            Gatilho.pagamento(PAGAMENTO_ESTORNADO, 7L, null, null));
        assertThat(d.aceita()).isFalse();
    }

    @Test
    void estornoEmCanceladaApenasLimpaPendente() {
        var d = decidir(estado(CANCELADA, 1, null, 9L), Gatilho.pagamento(PAGAMENTO_ESTORNADO, 9L, null, null));
        assertThat(d.transicoes()).isEmpty();
        assertThat(d.limparEstornoPendente()).isTrue();
    }

    @Test
    void estornoDeDuplicadaEmExecucaoLimpaPendente() {
        var d = decidir(estado(EM_EXECUCAO, 1, 8L, 9L), Gatilho.pagamento(PAGAMENTO_ESTORNADO, 9L, null, null));
        assertThat(d.aceita()).isTrue();
        assertThat(d.transicoes()).isEmpty();
        assertThat(d.limparEstornoPendente()).isTrue();
    }

    @Test
    void estornoEmAguardandoEstornoNaoLimpaPendenteExplicitamente() {
        var d = decidir(estado(AGUARDANDO_ESTORNO, 1, 9L, 9L), Gatilho.pagamento(PAGAMENTO_ESTORNADO, 9L, null, null));
        assertThat(d.limparEstornoPendente()).isFalse();
    }

    @Test
    void estornoSemPendenteEIgnorado() {
        assertThat(decidir(estado(CANCELADA, 1), Gatilho.pagamento(PAGAMENTO_ESTORNADO, 9L, null, null)).aceita())
            .isFalse();
    }

    @Test
    void estornoFalhouAlertaSemMudarStatus() {
        for (var s : List.of(AGUARDANDO_ESTORNO, CANCELADA)) {
            var d = decidir(estado(s, 1, 9L, 9L), Gatilho.pagamento(PAGAMENTO_ESTORNO_FALHOU, 9L, null, "x"));
            assertThat(d.transicoes()).isEmpty();
            assertThat(d.acoes()).containsExactly(Acao.ALERTAR_ESTORNO_FALHOU);
        }
        assertThat(decidir(estado(EM_EXECUCAO, 1, 9L, null),
            Gatilho.pagamento(PAGAMENTO_ESTORNO_FALHOU, 9L, null, "x")).aceita()).isFalse();
    }

    @Test
    void reprocessarEnviaEstornoDoPendente() {
        var d = decidir(estado(CANCELADA, 1, 9L, 8L), Gatilho.de(REPROCESSAR_ESTORNO));
        assertThat(d.pagamentoAEstornar()).isEqualTo(8L);
    }

    @Test
    void reprocessarSemPendenteEIgnorado() {
        assertThat(decidir(estado(CANCELADA, 1), Gatilho.de(REPROCESSAR_ESTORNO)).aceita()).isFalse();
    }

    @Test
    void reprocessarEmOutroStatusEIgnorado() {
        assertThat(decidir(estado(EM_EXECUCAO, 1, 9L, 8L), Gatilho.de(REPROCESSAR_ESTORNO)).aceita()).isFalse();
    }

    @Test
    void diagnosticoSemItensCancela() {
        var d = decidir(estado(EM_DIAGNOSTICO, 0), Gatilho.diagnostico(true));
        assertThat(d.transicoes()).containsExactly(StatusOS.CANCELADA);
        assertThat(d.motivoCancelamento()).isEqualTo("DIAGNOSTICO_SEM_ITENS");
    }

    @Test
    void gatilhoForaDeHoraEIgnorado() {
        var d = decidir(estado(ABERTA, 0), Gatilho.de(ORCAMENTO_APROVADO));
        assertThat(d.aceita()).isFalse();
        assertThat(d.transicoes()).isEmpty();
        assertThat(d.acoes()).isEmpty();
    }

    @Test
    void cancelamentoSolicitadoSoAntesDeEmExecucao() {
        assertThat(decidir(estado(EM_EXECUCAO, 1, 9L, null), Gatilho.de(CANCELAMENTO_SOLICITADO)).aceita())
            .isFalse();
    }

    @Test
    void todoGatilhoEmTodoStatusNaoLancaExcecao() {
        for (var s : StatusOS.values()) {
            for (var t : TipoGatilho.values()) {
                var d = decidir(estado(s, 1, 9L, 9L), new Gatilho(t, 9L, 1, "x", false));
                assertThat(d).isNotNull();
                if (!d.aceita()) {
                    assertThat(d.acoes()).isEmpty();
                }
            }
        }
    }

    @Test
    void canceladaSempreTrazMotivo() {
        for (var s : StatusOS.values()) {
            for (var t : TipoGatilho.values()) {
                var d = decidir(estado(s, 3, 9L, 9L), new Gatilho(t, 8L, 3, "x", t == DIAGNOSTICO_CONCLUIDO));
                if (d.acoes().contains(Acao.PUBLICAR_OS_CANCELADA) || d.acoes().contains(Acao.ENVIAR_ESTORNO)) {
                    assertThat(d.motivoCancelamento()).as("%s/%s", s, t).isNotBlank();
                }
            }
        }
    }
}
