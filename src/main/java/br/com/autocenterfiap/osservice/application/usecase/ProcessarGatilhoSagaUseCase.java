package br.com.autocenterfiap.osservice.application.usecase;

import br.com.autocenterfiap.osservice.application.exception.OrdemServicoNaoEncontradaException;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSagaFactory;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.Acao;
import br.com.autocenterfiap.osservice.domain.saga.Decisao;
import br.com.autocenterfiap.osservice.domain.saga.EstadoSaga;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.MaquinaEstadosSaga;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ProcessarGatilhoSagaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarGatilhoSagaUseCase.class);
    private static final int TAMANHO_MOTIVO_HISTORICO = 500;

    private final OrdemServicoRepositoryPort ordens;
    private final SagaRepositoryPort sagas;
    private final HistoricoRepositoryPort historico;
    private final OutboxPort outbox;
    private final MensagemSagaFactory fabrica;
    private final ObservabilidadePort observabilidade;
    private final Clock clock;
    private final int maxTentativas;
    private final int validadeMinutos;

    public ProcessarGatilhoSagaUseCase(OrdemServicoRepositoryPort ordens, SagaRepositoryPort sagas,
            HistoricoRepositoryPort historico, OutboxPort outbox, MensagemSagaFactory fabrica,
            ObservabilidadePort observabilidade, Clock clock, int maxTentativas, int validadeMinutos) {
        this.ordens = ordens;
        this.sagas = sagas;
        this.historico = historico;
        this.outbox = outbox;
        this.fabrica = fabrica;
        this.observabilidade = observabilidade;
        this.clock = clock;
        this.maxTentativas = maxTentativas;
        this.validadeMinutos = validadeMinutos;
    }

    @Transactional
    public Decisao executar(Long osId, Gatilho gatilho, String evento, ItensOS itens, Map<String, Object> dados) {
        var os = ordens.buscarPorId(osId).orElseThrow(() -> new OrdemServicoNaoEncontradaException(osId));
        var saga = sagas.buscar(osId);
        var decisao = MaquinaEstadosSaga.decidir(new EstadoSaga(os.getStatus(), saga.getTentativasPagamento(),
                saga.getPagamentoConfirmadoId(), saga.getEstornoPendenteId(), maxTentativas), gatilho);
        if (!decisao.aceita()) {
            log.info("Evento {} descartado: OS {} em {}", evento, osId, os.getStatus());
            observabilidade.eventoDescartado(evento);
            return decisao;
        }

        var agora = clock.instant();
        aplicarEfeitosDoGatilho(os, saga, gatilho, decisao, itens, dados, agora);
        var mudancas = aplicarTransicoes(os, gatilho, evento, decisao, agora);
        aplicarEstorno(saga, decisao);
        aplicarCobranca(os, saga, decisao, agora);
        guardarMensagens(os, saga, decisao);
        ordens.salvar(os);
        sagas.salvar(saga);
        registrarMetricas(osId, gatilho, decisao, mudancas);
        return decisao;
    }

    private void aplicarEfeitosDoGatilho(OrdemServico os, SagaOS saga, Gatilho gatilho, Decisao decisao,
            ItensOS itens, Map<String, Object> dados, Instant agora) {
        switch (gatilho.tipo()) {
            case DIAGNOSTICO_CONCLUIDO -> {
                if (!gatilho.semItens()) {
                    os.registrarItens(itens, agora);
                }
            }
            case ORCAMENTO_GERADO -> saga.setOrcamentoId((Long) dados.get("orcamentoId"));
            case PAGAMENTO_LINK_GERADO -> {
                saga.setPagamentoId((Long) dados.get("pagamentoId"));
                saga.setLinkPagamento((String) dados.get("url"));
            }
            case PAGAMENTO_CONFIRMADO -> {
                if (decisao.transicoes().contains(StatusOS.EM_EXECUCAO)) {
                    saga.setPagamentoConfirmadoId(gatilho.pagamentoId());
                }
            }
            default -> {
            }
        }
    }

    private List<Mudanca> aplicarTransicoes(OrdemServico os, Gatilho gatilho, String evento, Decisao decisao,
            Instant agora) {
        var motivo = motivoHistorico(gatilho, decisao);
        if (decisao.transicoes().isEmpty()) {
            if (registraSemMudanca(gatilho)) {
                historico.registrar(new RegistroHistorico(os.getId(), os.getStatus(), os.getStatus(), evento,
                        motivo, agora));
            }
            return List.of();
        }
        var mudancas = new ArrayList<Mudanca>();
        var entradaNoStatus = entradaNoStatusAtual(os);
        for (var novo : decisao.transicoes()) {
            var anterior = os.getStatus();
            os.mudarStatus(novo, agora);
            historico.registrar(new RegistroHistorico(os.getId(), anterior, novo, evento, motivo, agora));
            mudancas.add(new Mudanca(anterior, novo, Duration.between(entradaNoStatus, agora)));
            entradaNoStatus = agora;
        }
        return mudancas;
    }

    private static boolean registraSemMudanca(Gatilho gatilho) {
        return gatilho.tipo() == TipoGatilho.EXECUCAO_INICIADA || gatilho.tipo() == TipoGatilho.PAGAMENTO_CONFIRMADO;
    }

    private Instant entradaNoStatusAtual(OrdemServico os) {
        return historico.listar(os.getId()).stream()
                .filter(r -> r.statusAnterior() != r.statusNovo())
                .map(RegistroHistorico::data)
                .reduce((primeira, ultima) -> ultima)
                .orElse(os.getCriadaEm());
    }

    private static String motivoHistorico(Gatilho gatilho, Decisao decisao) {
        var motivo = decisao.motivoCancelamento() != null ? decisao.motivoCancelamento() : gatilho.motivo();
        return motivo != null && motivo.length() > TAMANHO_MOTIVO_HISTORICO
                ? motivo.substring(0, TAMANHO_MOTIVO_HISTORICO) : motivo;
    }

    private static void aplicarEstorno(SagaOS saga, Decisao decisao) {
        if (decisao.registrarEstornoPendente()) {
            saga.setEstornoPendenteId(decisao.pagamentoAEstornar());
        }
        if (decisao.limparEstornoPendente()) {
            saga.setEstornoPendenteId(null);
        }
    }

    private void aplicarCobranca(OrdemServico os, SagaOS saga, Decisao decisao, Instant agora) {
        if (decisao.acoes().contains(Acao.ENVIAR_GERAR_COBRANCA)) {
            saga.setTentativasPagamento(saga.getTentativasPagamento() + 1);
            saga.setPrazoPagamento(agora.plus(Duration.ofMinutes(validadeMinutos)));
        } else if (os.getStatus() != StatusOS.AGUARDANDO_PAGAMENTO && os.getStatus() != StatusOS.PAGAMENTO_RECUSADO) {
            saga.setPrazoPagamento(null);
        }
    }

    private void guardarMensagens(OrdemServico os, SagaOS saga, Decisao decisao) {
        decisao.acoes().stream()
                .filter(acao -> acao != Acao.ALERTAR_ESTORNO_FALHOU)
                .forEach(acao -> outbox.guardar(fabrica.criar(acao, os, saga, decisao)));
    }

    private void registrarMetricas(Long osId, Gatilho gatilho, Decisao decisao, List<Mudanca> mudancas) {
        mudancas.forEach(m -> {
            observabilidade.tempoNoStatus(m.anterior(), m.tempo());
            observabilidade.mudancaStatus(m.novo());
        });
        if (compensa(gatilho, decisao)) {
            observabilidade.sagaCompensada(decisao.motivoCancelamento());
        }
        if (decisao.acoes().contains(Acao.ALERTAR_ESTORNO_FALHOU)) {
            log.error("Estorno falhou: OS {}, pagamento {}, motivo {}", osId, gatilho.pagamentoId(), gatilho.motivo());
            observabilidade.estornoFalhou();
        }
    }

    private static boolean compensa(Gatilho gatilho, Decisao decisao) {
        var estornaOuCancela = decisao.acoes().contains(Acao.ENVIAR_ESTORNO)
                || decisao.acoes().contains(Acao.PUBLICAR_OS_CANCELADA);
        return estornaOuCancela
                && !decisao.limparEstornoPendente()
                && gatilho.tipo() != TipoGatilho.CANCELAMENTO_SOLICITADO
                && gatilho.tipo() != TipoGatilho.REPROCESSAR_ESTORNO;
    }

    private record Mudanca(StatusOS anterior, StatusOS novo, Duration tempo) {
    }
}
