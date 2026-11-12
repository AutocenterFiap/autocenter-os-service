package br.com.autocenterfiap.osservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.application.exception.OSJaEmAndamentoException;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.MensagemProcessadaPort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.ItemProduto;
import br.com.autocenterfiap.osservice.domain.entity.ItemServico;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest(properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class RepositoriosIT {

    private static final AtomicLong VEICULOS = new AtomicLong(1000);
    private static final Instant AGORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private OrdemServicoRepositoryPort ordens;
    @Autowired
    private SagaRepositoryPort sagas;
    @Autowired
    private HistoricoRepositoryPort historico;
    @Autowired
    private MensagemProcessadaPort processadas;

    private OrdemServico nova() {
        return OrdemServico.abrir(1L, VEICULOS.incrementAndGet(), "Barulho no motor", UUID.randomUUID(), AGORA);
    }

    private OrdemServico novaComStatus(StatusOS status) {
        var os = nova();
        os.mudarStatus(status, AGORA);
        return ordens.salvar(os);
    }

    @Test
    void salvaERecuperaOSComItens() {
        var os = nova();
        os.registrarItens(new ItensOS(List.of(new ItemServico(1L, "Troca de oleo", new BigDecimal("105.00"))),
                List.of(new ItemProduto(7L, "Filtro", 2, new BigDecimal("22.50")))), AGORA);

        var salva = ordens.salvar(os);
        var lida = ordens.buscarPorId(salva.getId()).orElseThrow();

        assertThat(lida.getCorrelationId()).isEqualTo(os.getCorrelationId());
        assertThat(lida.getStatus()).isEqualTo(StatusOS.ABERTA);
        assertThat(lida.getValorTotal()).isEqualByComparingTo("150.00");
        assertThat(lida.getItens().servicos()).containsExactly(new ItemServico(1L, "Troca de oleo", new BigDecimal("105.00")));
        assertThat(lida.getItens().produtos()).containsExactly(new ItemProduto(7L, "Filtro", 2, new BigDecimal("22.50")));
        assertThat(lida.getCriadaEm()).isEqualTo(AGORA);
        assertThat(lida.getVersion()).isEqualTo(salva.getVersion());
    }

    @Test
    void numeroDaOSEIgualAoId() {
        var salva = ordens.salvar(nova());

        assertThat(salva.getNumero()).isEqualTo(salva.getId());
        assertThat(ordens.buscarPorNumero(salva.getId()).orElseThrow().getId()).isEqualTo(salva.getId());
    }

    @Test
    void versaoAntigaGeraConflito() {
        var salva = ordens.salvar(nova());
        var copiaAntiga = ordens.buscarPorId(salva.getId()).orElseThrow();
        salva.mudarStatus(StatusOS.EM_DIAGNOSTICO, AGORA);
        ordens.salvar(salva);

        copiaAntiga.mudarStatus(StatusOS.CANCELADA, AGORA);

        assertThatThrownBy(() -> ordens.salvar(copiaAntiga)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void atualizaStatusEItensSemDuplicar() {
        var salva = ordens.salvar(nova());
        salva.registrarItens(new ItensOS(List.of(new ItemServico(1L, "A", new BigDecimal("10.00"))), List.of()), AGORA);
        var v2 = ordens.salvar(salva);
        v2.registrarItens(new ItensOS(List.of(new ItemServico(2L, "B", new BigDecimal("20.00"))), List.of()), AGORA);

        var v3 = ordens.salvar(v2);

        assertThat(ordens.buscarPorId(v3.getId()).orElseThrow().getItens().servicos())
                .containsExactly(new ItemServico(2L, "B", new BigDecimal("20.00")));
        assertThat(v3.getVersion()).isGreaterThan(v2.getVersion());
    }

    @Test
    void segundaOSAtivaDoMesmoVeiculoEhRejeitada() {
        var primeira = ordens.salvar(nova());

        var segunda = OrdemServico.abrir(2L, primeira.getVeiculoId(), "Outro", UUID.randomUUID(), AGORA);

        assertThatThrownBy(() -> ordens.salvar(segunda)).isInstanceOf(OSJaEmAndamentoException.class);
    }

    @Test
    void veiculoLiberadoAposStatusFinalAceitaNovaOS() {
        var primeira = ordens.salvar(nova());
        primeira.mudarStatus(StatusOS.CANCELADA, AGORA);
        ordens.salvar(primeira);

        var segunda = OrdemServico.abrir(2L, primeira.getVeiculoId(), "Outro", UUID.randomUUID(), AGORA);

        assertThat(ordens.salvar(segunda).getId()).isNotNull();
    }

    @Test
    void listaComFiltroDeStatusEPaginacao() {
        novaComStatus(StatusOS.ENTREGUE);
        novaComStatus(StatusOS.ENTREGUE);
        novaComStatus(StatusOS.ENTREGUE);

        var pagina = ordens.listar(StatusOS.ENTREGUE, 0, 2);

        assertThat(pagina.conteudo()).hasSize(2).allMatch(o -> o.getStatus() == StatusOS.ENTREGUE);
        assertThat(pagina.totalElementos()).isGreaterThanOrEqualTo(3);
        assertThat(pagina.tamanho()).isEqualTo(2);
        assertThat(ordens.listar(null, 0, 5).conteudo()).isNotEmpty();
    }

    @Test
    void registrarSeNovaRetornaFalseNaRepeticao() {
        var id = UUID.randomUUID();

        assertThat(processadas.registrarSeNova(id)).isTrue();
        assertThat(processadas.registrarSeNova(id)).isFalse();
    }

    @Test
    void salvaERecuperaSaga() {
        var os = ordens.salvar(nova());
        var saga = new SagaOS(os.getId());
        saga.setTentativasPagamento(2);
        saga.setPrazoPagamento(AGORA);
        saga.setLinkPagamento("https://pagar.exemplo/abc");
        saga.setOrcamentoId(5L);
        saga.setPagamentoId(6L);
        saga.setPagamentoConfirmadoId(7L);
        saga.setEstornoPendenteId(8L);
        sagas.salvar(saga);

        var lida = sagas.buscar(os.getId());

        assertThat(lida).usingRecursiveComparison().isEqualTo(saga);
        assertThat(sagas.buscar(-1L).getTentativasPagamento()).isZero();
    }

    @Test
    void prazoVencidoSoRetornaStatusDePagamento() {
        var vencidaAguardando = novaComStatus(StatusOS.AGUARDANDO_PAGAMENTO);
        var vencidaRecusada = novaComStatus(StatusOS.PAGAMENTO_RECUSADO);
        var vencidaEmExecucao = novaComStatus(StatusOS.EM_EXECUCAO);
        var noPrazo = novaComStatus(StatusOS.AGUARDANDO_PAGAMENTO);
        var passado = AGORA.minusSeconds(60);
        for (var os : List.of(vencidaAguardando, vencidaRecusada, vencidaEmExecucao)) {
            var s = new SagaOS(os.getId());
            s.setPrazoPagamento(passado);
            sagas.salvar(s);
        }
        var futura = new SagaOS(noPrazo.getId());
        futura.setPrazoPagamento(AGORA.plusSeconds(600));
        sagas.salvar(futura);

        var vencidas = sagas.osComPrazoVencido(AGORA, 100);

        assertThat(vencidas).contains(vencidaAguardando.getId(), vencidaRecusada.getId())
                .doesNotContain(vencidaEmExecucao.getId(), noPrazo.getId());
        assertThat(sagas.osComPrazoVencido(AGORA, 1)).hasSize(1);
    }

    @Test
    void historicoEhRegistradoEmOrdem() {
        var os = ordens.salvar(nova());
        historico.registrar(new RegistroHistorico(os.getId(), null, StatusOS.ABERTA, "os.criada", null, AGORA));
        historico.registrar(new RegistroHistorico(os.getId(), StatusOS.ABERTA, StatusOS.EM_DIAGNOSTICO,
                "diagnostico.iniciado", "ok", AGORA));

        var lista = historico.listar(os.getId());

        assertThat(lista).extracting(RegistroHistorico::statusNovo)
                .containsExactly(StatusOS.ABERTA, StatusOS.EM_DIAGNOSTICO);
        assertThat(lista.get(1).motivo()).isEqualTo("ok");
        assertThat(lista.get(0).statusAnterior()).isNull();
    }
}
