package br.com.autocenterfiap.osservice.application.mensagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import br.com.autocenterfiap.osservice.domain.entity.ItemProduto;
import br.com.autocenterfiap.osservice.domain.entity.ItemServico;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.Acao;
import br.com.autocenterfiap.osservice.domain.saga.Decisao;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MensagemSagaFactoryTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T14:30:00Z");

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MensagemSagaFactory factory;
    private OrdemServico os;
    private SagaOS saga;
    private Decisao decisao;

    @BeforeEach
    void preparar() {
        factory = new MensagemSagaFactory(mapper, Clock.fixed(AGORA, ZoneOffset.UTC), 30);
        var itens = new ItensOS(List.of(new ItemServico(1L, "Troca de oleo", new BigDecimal("105.00"))),
                List.of(new ItemProduto(7L, "Filtro", 1, new BigDecimal("45.00"))));
        os = OrdemServico.reconstruir(123L, 123L, 10L, 20L, "Barulho", StatusOS.AGUARDANDO_PAGAMENTO,
                new BigDecimal("150.00"), itens, UUID.randomUUID(), AGORA, AGORA, 1L);
        saga = new SagaOS(123L);
        saga.setOrcamentoId(55L);
        saga.setTentativasPagamento(2);
        decisao = new Decisao(true, List.of(), List.of(), 99L, "EXECUCAO_FALHOU", false, false);
    }

    @Test
    void gerarCobrancaTemCamposDoContrato() {
        var m = factory.criar(Acao.ENVIAR_GERAR_COBRANCA, os, saga, decisao);

        assertThat(m.topico()).isEqualTo("billing.comandos");
        assertThat(m.envelope().tipo()).isEqualTo("billing.gerar-cobranca");
        assertThat(m.envelope().payload().get("valorTotal").asText()).isEqualTo("150.00");
        assertThat(m.envelope().payload().get("validadeMinutos").asInt()).isEqualTo(30);
        assertThat(m.envelope().payload().get("orcamentoId").asLong()).isEqualTo(55L);
        assertThat(m.envelope().payload().get("tentativa").asInt()).isEqualTo(2);
        assertThat(m.envelope().correlationId()).isEqualTo(os.getCorrelationId());
        assertThat(m.envelope().osId()).isEqualTo(123L);
        assertThat(m.envelope().versao()).isEqualTo(1);
        assertThat(m.envelope().ocorridoEm()).isEqualTo(AGORA);
        assertThat(m.envelope().eventId()).isNotNull();
    }

    static Stream<Arguments> contrato() {
        return Stream.of(
                Arguments.of(Acao.ENVIAR_GERAR_ORCAMENTO, "billing.comandos", "billing.gerar-orcamento"),
                Arguments.of(Acao.ENVIAR_GERAR_COBRANCA, "billing.comandos", "billing.gerar-cobranca"),
                Arguments.of(Acao.ENVIAR_INICIAR_REPARO, "execucao.comandos", "execucao.iniciar-reparo"),
                Arguments.of(Acao.ENVIAR_ESTORNO, "billing.comandos", "billing.estornar-pagamento"),
                Arguments.of(Acao.PUBLICAR_OS_CANCELADA, "os.eventos", "os.cancelada"),
                Arguments.of(Acao.PUBLICAR_OS_CONCLUIDA, "os.eventos", "os.concluida"),
                Arguments.of(Acao.PUBLICAR_OS_ENTREGUE, "os.eventos", "os.entregue"));
    }

    @ParameterizedTest
    @MethodSource("contrato")
    void cadaAcaoUsaTopicoETipoDoContrato(Acao acao, String topico, String tipo) {
        var m = factory.criar(acao, os, saga, decisao);

        assertThat(m.topico()).isEqualTo(topico);
        assertThat(m.envelope().tipo()).isEqualTo(tipo);
        assertThat(m.envelope().payload()).isNotNull();
    }

    @Test
    void contratoCobreTodasAsAcoesExcetoAlerta() {
        var cobertas = contrato().map(a -> (Acao) a.get()[0]).toList();

        assertThat(cobertas).containsExactlyInAnyOrder(
                java.util.Arrays.stream(Acao.values()).filter(a -> a != Acao.ALERTAR_ESTORNO_FALHOU).toArray(Acao[]::new));
    }

    @Test
    void gerarOrcamentoEnviaItensComValoresTexto() {
        var p = factory.criar(Acao.ENVIAR_GERAR_ORCAMENTO, os, saga, decisao).envelope().payload();

        assertThat(p.get("clienteId").asLong()).isEqualTo(10L);
        assertThat(p.get("valorTotal").asText()).isEqualTo("150.00");
        assertThat(p.get("itens").get("servicos").get(0).get("valor").asText()).isEqualTo("105.00");
        assertThat(p.get("itens").get("servicos").get(0).get("servicoId").asLong()).isEqualTo(1L);
        assertThat(p.get("itens").get("produtos").get(0).get("precoUnitario").asText()).isEqualTo("45.00");
        assertThat(p.get("itens").get("produtos").get(0).get("quantidade").asInt()).isEqualTo(1);
    }

    @Test
    void estornoUsaPagamentoEMotivoDaDecisao() {
        var p = factory.criar(Acao.ENVIAR_ESTORNO, os, saga, decisao).envelope().payload();

        assertThat(p.get("pagamentoId").asLong()).isEqualTo(99L);
        assertThat(p.get("motivo").asText()).isEqualTo("EXECUCAO_FALHOU");
    }

    @Test
    void canceladaUsaMotivoDaDecisao() {
        var p = factory.criar(Acao.PUBLICAR_OS_CANCELADA, os, saga, decisao).envelope().payload();

        assertThat(p.get("motivo").asText()).isEqualTo("EXECUCAO_FALHOU");
    }

    @Test
    void osCriadaTemCamposDoContrato() {
        var m = factory.osCriada(os);

        assertThat(m.topico()).isEqualTo("os.eventos");
        assertThat(m.envelope().tipo()).isEqualTo("os.criada");
        assertThat(m.envelope().payload().get("numero").asLong()).isEqualTo(123L);
        assertThat(m.envelope().payload().get("clienteId").asLong()).isEqualTo(10L);
        assertThat(m.envelope().payload().get("veiculoId").asLong()).isEqualTo(20L);
        assertThat(m.envelope().payload().get("problemaRelatado").asText()).isEqualTo("Barulho");
    }

    @Test
    void alertaDeEstornoNaoGeraMensagem() {
        assertThatThrownBy(() -> factory.criar(Acao.ALERTAR_ESTORNO_FALHOU, os, saga, decisao))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void envelopeIgnoraCampoDesconhecido() throws Exception {
        var json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"tipo\":\"x\",\"versao\":1,\"osId\":1,"
                + "\"correlationId\":\"" + UUID.randomUUID() + "\",\"ocorridoEm\":\"2026-11-05T14:30:00Z\","
                + "\"payload\":{},\"novoCampo\":\"y\"}";

        var envelope = mapper.readValue(json, MensagemEnvelope.class);

        assertThat(envelope.tipo()).isEqualTo("x");
        assertThat(envelope.ocorridoEm()).isEqualTo(AGORA);
    }
}
