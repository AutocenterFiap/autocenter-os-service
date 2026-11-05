package br.com.autocenterfiap.osservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrdemServicoTest {

    private static final Instant T0 = Instant.parse("2026-11-05T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-05T11:00:00Z");

    private static OrdemServico nova() {
        return OrdemServico.abrir(1L, 2L, "Barulho no motor", UUID.randomUUID(), T0);
    }

    private static ItensOS itensExemplo() {
        return new ItensOS(
                List.of(new ItemServico(1L, "Troca de óleo", new BigDecimal("120.00"))),
                List.of(new ItemProduto(7L, "Filtro", 2, new BigDecimal("45.00"))));
    }

    @Test
    void abrirNasceAbertaSemItens() {
        var correlationId = UUID.randomUUID();
        var os = OrdemServico.abrir(1L, 2L, "Barulho", correlationId, T0);

        assertThat(os.getStatus()).isEqualTo(StatusOS.ABERTA);
        assertThat(os.getValorTotal()).isEqualByComparingTo("0.00");
        assertThat(os.getValorTotal().scale()).isEqualTo(2);
        assertThat(os.getItens().vazio()).isTrue();
        assertThat(os.getClienteId()).isEqualTo(1L);
        assertThat(os.getVeiculoId()).isEqualTo(2L);
        assertThat(os.getProblemaRelatado()).isEqualTo("Barulho");
        assertThat(os.getCorrelationId()).isEqualTo(correlationId);
        assertThat(os.getCriadaEm()).isEqualTo(T0);
        assertThat(os.getAtualizadaEm()).isEqualTo(T0);
        assertThat(os.getId()).isNull();
        assertThat(os.getNumero()).isNull();
    }

    @Test
    void totalSomaServicosEProdutosPorQuantidade() {
        assertThat(itensExemplo().total()).isEqualByComparingTo("210.00");
        assertThat(itensExemplo().total().scale()).isEqualTo(2);
    }

    @Test
    void itensVazioTemTotalZero() {
        var itens = new ItensOS(List.of(), List.of());
        assertThat(itens.vazio()).isTrue();
        assertThat(itens.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void finaisSaoEntregueReprovadoCancelada() {
        var finais = EnumSet.of(StatusOS.ENTREGUE, StatusOS.ORCAMENTO_REPROVADO, StatusOS.CANCELADA);
        for (var s : StatusOS.values()) {
            assertThat(s.isFinal()).as(s.name()).isEqualTo(finais.contains(s));
        }
    }

    @Test
    void bloqueiamNovaOSDeAbertaAteEmExecucao() {
        Set<StatusOS> bloqueiam = EnumSet.of(StatusOS.ABERTA, StatusOS.EM_DIAGNOSTICO,
                StatusOS.AGUARDANDO_APROVACAO, StatusOS.AGUARDANDO_PAGAMENTO,
                StatusOS.PAGAMENTO_RECUSADO, StatusOS.EM_EXECUCAO);
        for (var s : StatusOS.values()) {
            assertThat(s.bloqueiaNovaOS()).as(s.name()).isEqualTo(bloqueiam.contains(s));
        }
    }

    @Test
    void podeCancelarSomenteAntesDeEmExecucao() {
        Set<StatusOS> podem = EnumSet.of(StatusOS.ABERTA, StatusOS.EM_DIAGNOSTICO,
                StatusOS.AGUARDANDO_APROVACAO, StatusOS.AGUARDANDO_PAGAMENTO,
                StatusOS.PAGAMENTO_RECUSADO);
        for (var s : StatusOS.values()) {
            assertThat(s.podeCancelar()).as(s.name()).isEqualTo(podem.contains(s));
        }
    }

    @Test
    void mudarStatusAtualizaStatusEAtualizadaEm() {
        var os = nova();
        os.mudarStatus(StatusOS.EM_DIAGNOSTICO, T1);

        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_DIAGNOSTICO);
        assertThat(os.getAtualizadaEm()).isEqualTo(T1);
        assertThat(os.getCriadaEm()).isEqualTo(T0);
    }

    @Test
    void registrarItensDefineItensValorTotalEAtualizadaEm() {
        var os = nova();
        os.registrarItens(itensExemplo(), T1);

        assertThat(os.getItens().servicos()).hasSize(1);
        assertThat(os.getItens().produtos()).hasSize(1);
        assertThat(os.getValorTotal()).isEqualByComparingTo("210.00");
        assertThat(os.getValorTotal().scale()).isEqualTo(2);
        assertThat(os.getAtualizadaEm()).isEqualTo(T1);
    }

    @Test
    void reconstruirReidrataTodosOsCampos() {
        var cid = UUID.randomUUID();
        var os = OrdemServico.reconstruir(5L, 42L, 1L, 2L, "Freio", StatusOS.EM_EXECUCAO,
                new BigDecimal("210.00"), itensExemplo(), cid, T0, T1, 3L);

        assertThat(os.getId()).isEqualTo(5L);
        assertThat(os.getNumero()).isEqualTo(42L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.EM_EXECUCAO);
        assertThat(os.getValorTotal()).isEqualByComparingTo("210.00");
        assertThat(os.getItens().servicos()).hasSize(1);
        assertThat(os.getCorrelationId()).isEqualTo(cid);
        assertThat(os.getCriadaEm()).isEqualTo(T0);
        assertThat(os.getAtualizadaEm()).isEqualTo(T1);
        assertThat(os.getVersion()).isEqualTo(3L);
    }

    @Test
    void definirIdENumeroAposPrimeiroSave() {
        var os = nova();
        os.definirIdENumero(9L, 100L);
        assertThat(os.getId()).isEqualTo(9L);
        assertThat(os.getNumero()).isEqualTo(100L);
    }
}
