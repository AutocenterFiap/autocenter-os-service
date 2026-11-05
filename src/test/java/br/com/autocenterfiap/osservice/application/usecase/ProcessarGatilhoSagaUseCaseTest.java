package br.com.autocenterfiap.osservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.autocenterfiap.osservice.application.exception.OrdemServicoNaoEncontradaException;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSagaFactory;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSaida;
import br.com.autocenterfiap.osservice.application.mensagem.TiposMensagem;
import br.com.autocenterfiap.osservice.application.mensagem.Topicos;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.ItemProduto;
import br.com.autocenterfiap.osservice.domain.entity.ItemServico;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.Decisao;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class ProcessarGatilhoSagaUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T14:30:00Z");
    private static final Instant ANTES = AGORA.minus(Duration.ofMinutes(10));
    private static final Long OS_ID = 10L;
    private static final ItensOS ITENS = new ItensOS(
            List.of(new ItemServico(1L, "Troca de oleo", new BigDecimal("120.00"))),
            List.of(new ItemProduto(7L, "Filtro", 2, new BigDecimal("45.00"))));

    private final OrdemServicoRepositoryPort ordens = mock(OrdemServicoRepositoryPort.class);
    private final SagaRepositoryPort sagas = mock(SagaRepositoryPort.class);
    private final HistoricoRepositoryPort historico = mock(HistoricoRepositoryPort.class);
    private final OutboxPort outbox = mock(OutboxPort.class);
    private final ObservabilidadePort observabilidade = mock(ObservabilidadePort.class);
    private ProcessarGatilhoSagaUseCase useCase;
    private final Logger logUseCase = (Logger) LoggerFactory.getLogger(ProcessarGatilhoSagaUseCase.class);
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private SagaOS saga;

    @BeforeEach
    void preparar() {
        log.start();
        logUseCase.addAppender(log);
        var clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        var fabrica = new MensagemSagaFactory(new ObjectMapper().registerModule(new JavaTimeModule()), clock, 30);
        useCase = new ProcessarGatilhoSagaUseCase(ordens, sagas, historico, outbox, fabrica, observabilidade,
                clock, 3, 30);
        saga = new SagaOS(OS_ID);
        when(sagas.buscar(OS_ID)).thenReturn(saga);
    }

    private OrdemServico osEm(StatusOS status) {
        var os = OrdemServico.reconstruir(OS_ID, OS_ID, 1L, 2L, "Barulho", status, ITENS.total(), ITENS,
                UUID.randomUUID(), ANTES, ANTES, 0L);
        when(ordens.buscarPorId(OS_ID)).thenReturn(Optional.of(os));
        return os;
    }

    @AfterEach
    void soltarLog() {
        logUseCase.detachAppender(log);
    }

    private List<String> alertas() {
        return log.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private List<MensagemSaida> mensagens(int quantidade) {
        var captor = ArgumentCaptor.forClass(MensagemSaida.class);
        verify(outbox, times(quantidade)).guardar(captor.capture());
        return captor.getAllValues();
    }

    private List<RegistroHistorico> registros(int quantidade) {
        var captor = ArgumentCaptor.forClass(RegistroHistorico.class);
        verify(historico, times(quantidade)).registrar(captor.capture());
        return captor.getAllValues();
    }

    private Decisao executar(TipoGatilho tipo, String evento) {
        return useCase.executar(OS_ID, Gatilho.de(tipo), evento, null, Map.of());
    }

    @Test
    void aprovacaoEnviaCobrancaIncrementaTentativaEDefinePrazo() {
        var os = osEm(StatusOS.AGUARDANDO_APROVACAO);
        saga.setOrcamentoId(55L);

        executar(TipoGatilho.ORCAMENTO_APROVADO, TiposMensagem.ORCAMENTO_APROVADO);

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_PAGAMENTO);
        assertThat(saga.getTentativasPagamento()).isEqualTo(1);
        assertThat(saga.getPrazoPagamento()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
        var msg = mensagens(1).get(0);
        assertThat(msg.topico()).isEqualTo(Topicos.BILLING_COMANDOS);
        assertThat(msg.envelope().tipo()).isEqualTo("billing.gerar-cobranca");
        assertThat(msg.envelope().payload().get("tentativa").asInt()).isEqualTo(1);
        assertThat(msg.envelope().payload().get("orcamentoId").asLong()).isEqualTo(55L);
        assertThat(msg.envelope().payload().get("validadeMinutos").asInt()).isEqualTo(30);
        assertThat(registros(1).get(0)).isEqualTo(new RegistroHistorico(OS_ID, StatusOS.AGUARDANDO_APROVACAO,
                StatusOS.AGUARDANDO_PAGAMENTO, TiposMensagem.ORCAMENTO_APROVADO, null, AGORA));
        verify(ordens).salvar(os);
        verify(sagas).salvar(saga);
    }

    @Test
    void diagnosticoConcluidoSalvaItensETotalEEnviaOrcamento() {
        var os = OrdemServico.reconstruir(OS_ID, OS_ID, 1L, 2L, "Barulho", StatusOS.EM_DIAGNOSTICO,
                BigDecimal.ZERO.setScale(2), ItensOS.VAZIO, UUID.randomUUID(), ANTES, ANTES, 0L);
        when(ordens.buscarPorId(OS_ID)).thenReturn(Optional.of(os));

        useCase.executar(OS_ID, Gatilho.diagnostico(false), TiposMensagem.DIAGNOSTICO_CONCLUIDO, ITENS, Map.of());

        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(os.getItens()).isEqualTo(ITENS);
        assertThat(os.getValorTotal()).isEqualByComparingTo("210.00");
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().tipo()).isEqualTo("billing.gerar-orcamento");
        assertThat(msg.envelope().payload().get("valorTotal").asText()).isEqualTo("210.00");
        assertThat(msg.envelope().payload().get("itens").get("servicos")).hasSize(1);
        verify(historico, never()).registrar(any());
        verify(ordens).salvar(os);
    }

    @Test
    void diagnosticoSemItensCancelaSemGravarItens() {
        var os = osEm(StatusOS.EM_DIAGNOSTICO);

        useCase.executar(OS_ID, Gatilho.diagnostico(true), TiposMensagem.DIAGNOSTICO_CONCLUIDO, ItensOS.VAZIO,
                Map.of());

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(os.getItens()).isEqualTo(ITENS);
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().tipo()).isEqualTo("os.cancelada");
        assertThat(msg.envelope().payload().get("motivo").asText()).isEqualTo("DIAGNOSTICO_SEM_ITENS");
        assertThat(registros(1).get(0).motivo()).isEqualTo("DIAGNOSTICO_SEM_ITENS");
        verify(observabilidade).sagaCompensada("DIAGNOSTICO_SEM_ITENS");
    }

    @Test
    void orcamentoGeradoGravaOrcamentoId() {
        var os = osEm(StatusOS.EM_DIAGNOSTICO);

        useCase.executar(OS_ID, Gatilho.de(TipoGatilho.ORCAMENTO_GERADO), TiposMensagem.ORCAMENTO_GERADO, null,
                Map.of("orcamentoId", 55L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_APROVACAO);
        assertThat(saga.getOrcamentoId()).isEqualTo(55L);
        verify(outbox, never()).guardar(any());
    }

    @Test
    void novoLinkVoltaParaAguardandoESalvaLink() {
        var os = osEm(StatusOS.PAGAMENTO_RECUSADO);
        var prazo = AGORA.plus(Duration.ofMinutes(20));
        saga.setPrazoPagamento(prazo);
        saga.setTentativasPagamento(2);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_LINK_GERADO, 77L, 2, null),
                TiposMensagem.PAGAMENTO_LINK_GERADO, null, Map.of("pagamentoId", 77L, "url", "http://pague/2"));

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_PAGAMENTO);
        assertThat(saga.getLinkPagamento()).isEqualTo("http://pague/2");
        assertThat(saga.getPagamentoId()).isEqualTo(77L);
        assertThat(saga.getPrazoPagamento()).as("o prazo da cobranca vigente continua valendo").isEqualTo(prazo);
        assertThat(saga.getTentativasPagamento()).isEqualTo(2);
        verify(outbox, never()).guardar(any());
        assertThat(registros(1).get(0).statusAnterior()).isEqualTo(StatusOS.PAGAMENTO_RECUSADO);
    }

    @Test
    void linkEmAguardandoSoSalvaLink() {
        var os = osEm(StatusOS.AGUARDANDO_PAGAMENTO);
        var prazo = AGORA.plus(Duration.ofMinutes(20));
        saga.setPrazoPagamento(prazo);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_LINK_GERADO, 77L, 1, null),
                TiposMensagem.PAGAMENTO_LINK_GERADO, null, Map.of("pagamentoId", 77L, "url", "http://pague/1"));

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_PAGAMENTO);
        assertThat(saga.getLinkPagamento()).isEqualTo("http://pague/1");
        assertThat(saga.getPagamentoId()).isEqualTo(77L);
        assertThat(saga.getPrazoPagamento()).isEqualTo(prazo);
        verify(historico, never()).registrar(any());
    }

    @Test
    void confirmacaoGravaConfirmadoELimpaPrazo() {
        var os = osEm(StatusOS.AGUARDANDO_PAGAMENTO);
        saga.setPrazoPagamento(AGORA.plus(Duration.ofMinutes(20)));
        saga.setPagamentoId(77L);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_CONFIRMADO, 77L, null, null),
                TiposMensagem.PAGAMENTO_CONFIRMADO, null, Map.of("pagamentoId", 77L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(saga.getPagamentoConfirmadoId()).isEqualTo(77L);
        assertThat(saga.getPrazoPagamento()).isNull();
        assertThat(saga.getEstornoPendenteId()).isNull();
        var msg = mensagens(1).get(0);
        assertThat(msg.topico()).isEqualTo(Topicos.EXECUCAO_COMANDOS);
        assertThat(msg.envelope().tipo()).isEqualTo("execucao.iniciar-reparo");
        verify(observabilidade, never()).sagaCompensada(any());
    }

    @Test
    void confirmacaoAtrasadaEstornaRegistraENaoViraConfirmado() {
        var os = osEm(StatusOS.CANCELADA);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_CONFIRMADO, 77L, null, null),
                TiposMensagem.PAGAMENTO_CONFIRMADO, null, Map.of("pagamentoId", 77L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(saga.getPagamentoConfirmadoId()).isNull();
        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().tipo()).isEqualTo("billing.estornar-pagamento");
        assertThat(msg.envelope().payload().get("pagamentoId").asLong()).isEqualTo(77L);
        assertThat(msg.envelope().payload().get("motivo").asText()).isEqualTo("PAGAMENTO_ATRASADO");
        assertThat(registros(1).get(0)).isEqualTo(new RegistroHistorico(OS_ID, StatusOS.CANCELADA,
                StatusOS.CANCELADA, TiposMensagem.PAGAMENTO_CONFIRMADO, "PAGAMENTO_ATRASADO", AGORA));
        verify(observabilidade).sagaCompensada("PAGAMENTO_ATRASADO");
        verify(observabilidade, never()).mudancaStatus(any());
    }

    @Test
    void confirmacaoDuplicadaComVagaOcupadaEstornaSemSobrescreverPendente() {
        osEm(StatusOS.AGUARDANDO_ESTORNO);
        saga.setPagamentoConfirmadoId(77L);
        saga.setEstornoPendenteId(77L);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_CONFIRMADO, 88L, null, null),
                TiposMensagem.PAGAMENTO_CONFIRMADO, null, Map.of("pagamentoId", 88L));

        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        assertThat(saga.getPagamentoConfirmadoId()).isEqualTo(77L);
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().tipo()).isEqualTo("billing.estornar-pagamento");
        assertThat(msg.envelope().payload().get("pagamentoId").asLong()).isEqualTo(88L);
        assertThat(msg.envelope().payload().get("motivo").asText()).isEqualTo("PAGAMENTO_DUPLICADO");
    }

    @Test
    void confirmacaoDuplicadaComVagaOcupadaAlertaSegundoEstorno() {
        osEm(StatusOS.AGUARDANDO_ESTORNO);
        saga.setPagamentoConfirmadoId(77L);
        saga.setEstornoPendenteId(77L);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_CONFIRMADO, 88L, null, null),
                TiposMensagem.PAGAMENTO_CONFIRMADO, null, Map.of("pagamentoId", 88L));

        assertThat(alertas()).hasSize(1);
        assertThat(alertas().get(0)).contains("77").contains("88");
    }

    @Test
    void execucaoFalhouComVagaOcupadaEstornaOConfirmadoEAlerta() {
        var os = osEm(StatusOS.EM_EXECUCAO);
        saga.setPagamentoConfirmadoId(77L);
        saga.setEstornoPendenteId(88L);

        executar(TipoGatilho.EXECUCAO_FALHOU, TiposMensagem.EXECUCAO_FALHOU);

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_ESTORNO);
        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().tipo()).isEqualTo("billing.estornar-pagamento");
        assertThat(msg.envelope().payload().get("pagamentoId").asLong()).isEqualTo(77L);
        assertThat(alertas()).hasSize(1);
        assertThat(alertas().get(0)).contains("77").contains("88");
    }

    @Test
    void estornoComVagaLivreOuDoMesmoPagamentoNaoAlerta() {
        osEm(StatusOS.EM_EXECUCAO);
        saga.setPagamentoConfirmadoId(77L);
        executar(TipoGatilho.EXECUCAO_FALHOU, TiposMensagem.EXECUCAO_FALHOU);

        executar(TipoGatilho.REPROCESSAR_ESTORNO, "reprocessar-estorno");

        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        assertThat(alertas()).isEmpty();
    }

    @Test
    void estornoGravaPendente() {
        var os = osEm(StatusOS.EM_EXECUCAO);
        saga.setPagamentoConfirmadoId(77L);

        executar(TipoGatilho.EXECUCAO_FALHOU, TiposMensagem.EXECUCAO_FALHOU);

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_ESTORNO);
        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        assertThat(saga.getPagamentoConfirmadoId()).isEqualTo(77L);
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().tipo()).isEqualTo("billing.estornar-pagamento");
        assertThat(msg.envelope().payload().get("pagamentoId").asLong()).isEqualTo(77L);
        assertThat(msg.envelope().payload().get("motivo").asText()).isEqualTo("EXECUCAO_FALHOU");
        verify(observabilidade).sagaCompensada("EXECUCAO_FALHOU");
    }

    @Test
    void estornoConfirmadoLimpaPendenteCancelaEContaCompensacaoUmaVezSo() {
        var os = osEm(StatusOS.AGUARDANDO_ESTORNO);
        saga.setPagamentoConfirmadoId(77L);
        saga.setEstornoPendenteId(77L);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_ESTORNADO, 77L, null, null),
                TiposMensagem.PAGAMENTO_ESTORNADO, null, Map.of("pagamentoId", 77L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(saga.getEstornoPendenteId()).isNull();
        assertThat(mensagens(1).get(0).envelope().tipo()).isEqualTo("os.cancelada");
        verify(observabilidade, never()).sagaCompensada(any());
    }

    @Test
    void reprocessarEstornoReenviaSemAlterarVagaNemContarCompensacao() {
        osEm(StatusOS.AGUARDANDO_ESTORNO);
        saga.setEstornoPendenteId(77L);

        executar(TipoGatilho.REPROCESSAR_ESTORNO, "reprocessar-estorno");

        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        var msg = mensagens(1).get(0);
        assertThat(msg.envelope().payload().get("pagamentoId").asLong()).isEqualTo(77L);
        assertThat(msg.envelope().payload().get("motivo").asText()).isEqualTo("REPROCESSAMENTO_MANUAL");
        verify(observabilidade, never()).sagaCompensada(any());
    }

    @Test
    void recusaEnviaNovaCobrancaEReiniciaPrazo() {
        var os = osEm(StatusOS.AGUARDANDO_PAGAMENTO);
        saga.setTentativasPagamento(1);
        saga.setPrazoPagamento(AGORA.plus(Duration.ofMinutes(1)));

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_RECUSADO, 77L, 1, "cartao negado"),
                TiposMensagem.PAGAMENTO_RECUSADO, null, Map.of("pagamentoId", 77L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.PAGAMENTO_RECUSADO);
        assertThat(saga.getTentativasPagamento()).isEqualTo(2);
        assertThat(saga.getPrazoPagamento()).isEqualTo(AGORA.plus(Duration.ofMinutes(30)));
        assertThat(mensagens(1).get(0).envelope().payload().get("tentativa").asInt()).isEqualTo(2);
        assertThat(registros(1).get(0).motivo()).isEqualTo("cartao negado");
    }

    @Test
    void motivoLongoDoParticipanteCabeNoHistorico() {
        osEm(StatusOS.AGUARDANDO_PAGAMENTO);
        saga.setTentativasPagamento(1);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_RECUSADO, 77L, 1, "x".repeat(600)),
                TiposMensagem.PAGAMENTO_RECUSADO, null, Map.of("pagamentoId", 77L));

        assertThat(registros(1).get(0).motivo()).hasSize(500);
    }

    @Test
    void transicaoDuplaGravaDoisHistoricos() {
        var os = osEm(StatusOS.AGUARDANDO_PAGAMENTO);
        saga.setTentativasPagamento(3);
        saga.setPrazoPagamento(AGORA.plus(Duration.ofMinutes(1)));

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_RECUSADO, 77L, 3, "negado"),
                TiposMensagem.PAGAMENTO_RECUSADO, null, Map.of("pagamentoId", 77L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        var lista = registros(2);
        assertThat(lista.get(0).statusAnterior()).isEqualTo(StatusOS.AGUARDANDO_PAGAMENTO);
        assertThat(lista.get(0).statusNovo()).isEqualTo(StatusOS.PAGAMENTO_RECUSADO);
        assertThat(lista.get(1).statusAnterior()).isEqualTo(StatusOS.PAGAMENTO_RECUSADO);
        assertThat(lista.get(1).statusNovo()).isEqualTo(StatusOS.CANCELADA);
        assertThat(lista).allMatch(r -> "PAGAMENTO_RECUSADO_LIMITE".equals(r.motivo()));
        assertThat(saga.getTentativasPagamento()).isEqualTo(3);
        assertThat(saga.getPrazoPagamento()).isNull();
        assertThat(mensagens(1).get(0).envelope().tipo()).isEqualTo("os.cancelada");
        verify(observabilidade).mudancaStatus(StatusOS.PAGAMENTO_RECUSADO);
        verify(observabilidade).mudancaStatus(StatusOS.CANCELADA);
        verify(observabilidade).tempoNoStatus(StatusOS.PAGAMENTO_RECUSADO, Duration.ZERO);
        verify(observabilidade).sagaCompensada("PAGAMENTO_RECUSADO_LIMITE");
    }

    @Test
    void prazoVencidoCancelaELimpaPrazo() {
        var os = osEm(StatusOS.PAGAMENTO_RECUSADO);
        saga.setPrazoPagamento(AGORA.minus(Duration.ofMinutes(1)));

        executar(TipoGatilho.PRAZO_VENCIDO, "prazo.vencido");

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(saga.getPrazoPagamento()).isNull();
        verify(observabilidade).sagaCompensada("LINK_EXPIRADO");
    }

    @Test
    void cancelamentoSolicitadoLimpaPrazoENaoContaCompensacao() {
        var os = osEm(StatusOS.AGUARDANDO_PAGAMENTO);
        saga.setPrazoPagamento(AGORA.plus(Duration.ofMinutes(1)));

        executar(TipoGatilho.CANCELAMENTO_SOLICITADO, "cancelamento.solicitado");

        assertThat(os.getStatus()).isEqualTo(StatusOS.CANCELADA);
        assertThat(saga.getPrazoPagamento()).isNull();
        assertThat(mensagens(1).get(0).envelope().payload().get("motivo").asText())
                .isEqualTo("CANCELAMENTO_SOLICITADO");
        verify(observabilidade, never()).sagaCompensada(any());
    }

    @Test
    void estornoFalhouSoAlertaSemMensagem() {
        var os = osEm(StatusOS.AGUARDANDO_ESTORNO);
        saga.setEstornoPendenteId(77L);

        useCase.executar(OS_ID, Gatilho.pagamento(TipoGatilho.PAGAMENTO_ESTORNO_FALHOU, 77L, null, "erro no gateway"),
                TiposMensagem.PAGAMENTO_ESTORNO_FALHOU, null, Map.of("pagamentoId", 77L));

        assertThat(os.getStatus()).isEqualTo(StatusOS.AGUARDANDO_ESTORNO);
        assertThat(saga.getEstornoPendenteId()).isEqualTo(77L);
        verify(observabilidade).estornoFalhou();
        verify(outbox, never()).guardar(any());
        verify(historico, never()).registrar(any());
    }

    @Test
    void execucaoIniciadaRegistraNoHistoricoSemMudarStatus() {
        var os = osEm(StatusOS.EM_EXECUCAO);

        executar(TipoGatilho.EXECUCAO_INICIADA, TiposMensagem.EXECUCAO_INICIADA);

        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(registros(1).get(0)).isEqualTo(new RegistroHistorico(OS_ID, StatusOS.EM_EXECUCAO,
                StatusOS.EM_EXECUCAO, TiposMensagem.EXECUCAO_INICIADA, null, AGORA));
        verify(observabilidade, never()).mudancaStatus(any());
        verify(observabilidade, never()).tempoNoStatus(any(), any());
    }

    @Test
    void execucaoFinalizadaConcluiEPublica() {
        var os = osEm(StatusOS.EM_EXECUCAO);

        executar(TipoGatilho.EXECUCAO_FINALIZADA, TiposMensagem.EXECUCAO_FINALIZADA);

        assertThat(os.getStatus()).isEqualTo(StatusOS.CONCLUIDA);
        assertThat(mensagens(1).get(0).envelope().tipo()).isEqualTo("os.concluida");
    }

    @Test
    void transicaoMedeTempoDesdeAEntradaNoStatusIgnorandoRegistrosSemMudanca() {
        osEm(StatusOS.EM_EXECUCAO);
        var entrada = AGORA.minus(Duration.ofHours(2));
        when(historico.listar(OS_ID)).thenReturn(List.of(
                new RegistroHistorico(OS_ID, StatusOS.AGUARDANDO_PAGAMENTO, StatusOS.EM_EXECUCAO,
                        TiposMensagem.PAGAMENTO_CONFIRMADO, null, entrada),
                new RegistroHistorico(OS_ID, StatusOS.EM_EXECUCAO, StatusOS.EM_EXECUCAO,
                        TiposMensagem.EXECUCAO_INICIADA, null, AGORA.minus(Duration.ofMinutes(5)))));

        executar(TipoGatilho.EXECUCAO_FINALIZADA, TiposMensagem.EXECUCAO_FINALIZADA);

        verify(observabilidade).tempoNoStatus(StatusOS.EM_EXECUCAO, Duration.ofHours(2));
        verify(observabilidade).mudancaStatus(StatusOS.CONCLUIDA);
    }

    @Test
    void semHistoricoMedeTempoDesdeACriacao() {
        osEm(StatusOS.ABERTA);

        executar(TipoGatilho.DIAGNOSTICO_INICIADO, TiposMensagem.DIAGNOSTICO_INICIADO);

        verify(observabilidade).tempoNoStatus(StatusOS.ABERTA, Duration.ofMinutes(10));
    }

    @Test
    void decisaoIgnoradaNaoAlteraNadaEContaDescarte() {
        var os = osEm(StatusOS.ABERTA);

        var aceita = executar(TipoGatilho.EXECUCAO_FINALIZADA, TiposMensagem.EXECUCAO_FINALIZADA).aceita();

        assertThat(aceita).isFalse();
        assertThat(os.getStatus()).isEqualTo(StatusOS.ABERTA);
        verify(ordens, never()).salvar(any());
        verify(sagas, never()).salvar(any());
        verifyNoInteractions(historico, outbox);
        verify(observabilidade).eventoDescartado(TiposMensagem.EXECUCAO_FINALIZADA);
    }

    @Test
    void osInexistenteLancaExcecao() {
        when(ordens.buscarPorId(OS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(OS_ID, Gatilho.de(TipoGatilho.DIAGNOSTICO_INICIADO), "x", null,
                Map.of())).isInstanceOf(OrdemServicoNaoEncontradaException.class)
                .hasMessageContaining("10");
    }

    @Test
    void metricasSaemSoDepoisDeSalvarParaNaoContarDuasVezesNaRetentativa() {
        var os = osEm(StatusOS.ABERTA);
        when(ordens.salvar(os)).thenThrow(new IllegalStateException("conflito"));

        assertThatThrownBy(() -> executar(TipoGatilho.DIAGNOSTICO_INICIADO, TiposMensagem.DIAGNOSTICO_INICIADO))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(observabilidade);
    }

    @Test
    void salvaOSAntesDaSaga() {
        var os = osEm(StatusOS.ABERTA);

        executar(TipoGatilho.DIAGNOSTICO_INICIADO, TiposMensagem.DIAGNOSTICO_INICIADO);

        var ordem = inOrder(ordens, sagas);
        ordem.verify(ordens).salvar(os);
        ordem.verify(sagas).salvar(saga);
    }
}
