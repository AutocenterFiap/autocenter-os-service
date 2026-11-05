package br.com.autocenterfiap.osservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.mensagem.EntradaSaga;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MapeadorGatilhoTest {

    private static final String ITENS = """
            {"itens":{
              "servicos":[{"servicoId":1,"descricao":"Troca de oleo","valor":"120.00"}],
              "produtos":[{"produtoId":7,"descricao":"Filtro","quantidade":2,"precoUnitario":"45.00"}]}}""";

    private final ObjectMapper json = new ObjectMapper();
    private final MapeadorGatilho mapeador = new MapeadorGatilho();

    private MensagemEnvelope envelope(String tipo, String payload) throws JsonProcessingException {
        return new MensagemEnvelope(UUID.randomUUID(), tipo, 1, 10L, UUID.randomUUID(), Instant.now(),
                payload == null ? null : json.readTree(payload));
    }

    private EntradaSaga mapear(String tipo, String payload) throws JsonProcessingException {
        return mapeador.mapear(envelope(tipo, payload)).orElseThrow();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            diagnostico.iniciado     | {"mecanicoId":3}                                 | DIAGNOSTICO_INICIADO
            orcamento.gerado         | {"orcamentoId":55,"valorTotal":"210.00"}         | ORCAMENTO_GERADO
            orcamento.falhou         | {"motivo":"sem tabela"}                          | ORCAMENTO_FALHOU
            orcamento.aprovado       | {"orcamentoId":55}                               | ORCAMENTO_APROVADO
            orcamento.reprovado      | {"orcamentoId":55,"motivo":"caro"}               | ORCAMENTO_REPROVADO
            pagamento.link-gerado    | {"pagamentoId":77,"url":"http://p","tentativa":1} | PAGAMENTO_LINK_GERADO
            pagamento.confirmado     | {"pagamentoId":77,"valor":"210.00"}              | PAGAMENTO_CONFIRMADO
            pagamento.recusado       | {"pagamentoId":77,"tentativa":2,"motivo":"x"}    | PAGAMENTO_RECUSADO
            pagamento.estornado      | {"pagamentoId":77}                               | PAGAMENTO_ESTORNADO
            pagamento.estorno-falhou | {"pagamentoId":77,"motivo":"x"}                  | PAGAMENTO_ESTORNO_FALHOU
            execucao.iniciada        | {"mecanicoId":3}                                 | EXECUCAO_INICIADA
            execucao.finalizada      | {}                                               | EXECUCAO_FINALIZADA
            execucao.falhou          | {"motivo":"peca"}                                | EXECUCAO_FALHOU
            """)
    void mapeiaTiposDoContrato(String tipo, String payload, TipoGatilho esperado) throws Exception {
        assertThat(mapear(tipo, payload).gatilho().tipo()).isEqualTo(esperado);
    }

    @Test
    void diagnosticoConcluidoMapeiaItensComPrecos() throws Exception {
        var entrada = mapear("diagnostico.concluido", ITENS);

        assertThat(entrada.gatilho().tipo()).isEqualTo(TipoGatilho.DIAGNOSTICO_CONCLUIDO);
        assertThat(entrada.gatilho().semItens()).isFalse();
        assertThat(entrada.itens().servicos()).hasSize(1);
        assertThat(entrada.itens().servicos().get(0).servicoId()).isEqualTo(1L);
        assertThat(entrada.itens().servicos().get(0).descricao()).isEqualTo("Troca de oleo");
        assertThat(entrada.itens().servicos().get(0).valor()).isEqualByComparingTo("120.00");
        assertThat(entrada.itens().produtos().get(0).produtoId()).isEqualTo(7L);
        assertThat(entrada.itens().produtos().get(0).quantidade()).isEqualTo(2);
        assertThat(entrada.itens().produtos().get(0).precoUnitario()).isEqualTo(new BigDecimal("45.00"));
    }

    @Test
    void diagnosticoComListasVaziasEstaSemItens() throws Exception {
        var entrada = mapear("diagnostico.concluido", "{\"itens\":{\"servicos\":[],\"produtos\":[]}}");

        assertThat(entrada.gatilho().semItens()).isTrue();
        assertThat(entrada.itens().vazio()).isTrue();
    }

    @Test
    void listaAusenteContaComoVaziaSeAOutraVier() throws Exception {
        var entrada = mapear("diagnostico.concluido",
                "{\"itens\":{\"servicos\":[{\"servicoId\":1,\"descricao\":\"d\",\"valor\":\"1.00\"}]}}");

        assertThat(entrada.gatilho().semItens()).isFalse();
        assertThat(entrada.itens().produtos()).isEmpty();
    }

    @Test
    void outrosEventosNaoTrazemItens() throws Exception {
        assertThat(mapear("execucao.finalizada", "{}").itens()).isNull();
    }

    @Test
    void orcamentoGeradoLevaOrcamentoIdNosDados() throws Exception {
        assertThat(mapear("orcamento.gerado", "{\"orcamentoId\":55}").dados())
                .containsExactlyEntriesOf(Map.of("orcamentoId", 55L));
    }

    @Test
    void linkGeradoLevaPagamentoEUrlNosDados() throws Exception {
        var entrada = mapear("pagamento.link-gerado", "{\"pagamentoId\":77,\"url\":\"http://p\",\"tentativa\":2}");

        assertThat(entrada.dados()).containsOnly(Map.entry("pagamentoId", 77L), Map.entry("url", "http://p"));
        assertThat(entrada.gatilho().pagamentoId()).isEqualTo(77L);
        assertThat(entrada.gatilho().tentativa()).isEqualTo(2);
    }

    @Test
    void confirmadoLevaPagamentoIdNoGatilho() throws Exception {
        assertThat(mapear("pagamento.confirmado", "{\"pagamentoId\":77}").gatilho().pagamentoId()).isEqualTo(77L);
    }

    @Test
    void recusadoLevaTentativaEMotivo() throws Exception {
        var gatilho = mapear("pagamento.recusado", "{\"pagamentoId\":77,\"tentativa\":2,\"motivo\":\"negado\"}")
                .gatilho();

        assertThat(gatilho.pagamentoId()).isEqualTo(77L);
        assertThat(gatilho.tentativa()).isEqualTo(2);
        assertThat(gatilho.motivo()).isEqualTo("negado");
    }

    @Test
    void motivoAusenteNaoInvalidaAMensagem() throws Exception {
        assertThat(mapear("pagamento.estorno-falhou", "{\"pagamentoId\":77}").gatilho().motivo()).isNull();
        assertThat(mapear("execucao.falhou", "{}").gatilho().tipo()).isEqualTo(TipoGatilho.EXECUCAO_FALHOU);
    }

    @Test
    void payloadNuloEmEventoSemCamposEAceito() throws Exception {
        assertThat(mapear("execucao.finalizada", null).gatilho().tipo()).isEqualTo(TipoGatilho.EXECUCAO_FINALIZADA);
    }

    @Test
    void tipoDesconhecidoRetornaVazio() throws Exception {
        assertThat(mapeador.mapear(envelope("evento.qualquer", "{}"))).isEmpty();
        assertThat(mapeador.mapear(envelope("os.criada", "{}"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            pagamento.confirmado     | {"valor":"210.00"}
            pagamento.confirmado     | {"pagamentoId":null}
            pagamento.confirmado     | {"pagamentoId":"abc"}
            pagamento.confirmado     | {"pagamentoId":1.5}
            pagamento.recusado       | {"tentativa":1}
            pagamento.recusado       | {"pagamentoId":77}
            pagamento.recusado       | {"pagamentoId":77,"tentativa":"um"}
            pagamento.estornado      | {}
            pagamento.estorno-falhou | {"motivo":"x"}
            pagamento.link-gerado    | {"url":"http://p"}
            pagamento.link-gerado    | {"pagamentoId":77}
            pagamento.link-gerado    | {"pagamentoId":77,"url":"  "}
            orcamento.gerado         | {"valorTotal":"210.00"}
            orcamento.aprovado       | {}
            orcamento.reprovado      | {"motivo":"caro"}
            diagnostico.concluido    | {}
            diagnostico.concluido    | {"itens":null}
            diagnostico.concluido    | {"itens":{}}
            diagnostico.concluido    | {"itens":[]}
            diagnostico.concluido    | {"itens":{"servicos":"x"}}
            diagnostico.concluido    | {"itens":{"servicos":[{"descricao":"d","valor":"1.00"}]}}
            diagnostico.concluido    | {"itens":{"servicos":[{"servicoId":1,"valor":"1.00"}]}}
            diagnostico.concluido    | {"itens":{"servicos":[{"servicoId":1,"descricao":"d"}]}}
            diagnostico.concluido    | {"itens":{"servicos":[{"servicoId":1,"descricao":"d","valor":"abc"}]}}
            diagnostico.concluido    | {"itens":{"servicos":[{"servicoId":1,"descricao":"d","valor":"-1.00"}]}}
            diagnostico.concluido    | {"itens":{"produtos":[{"produtoId":7,"descricao":"f","precoUnitario":"1.00"}]}}
            diagnostico.concluido    | {"itens":{"produtos":[{"produtoId":7,"descricao":"f","quantidade":0,"precoUnitario":"1.00"}]}}
            diagnostico.concluido    | {"itens":{"produtos":[{"produtoId":7,"descricao":"f","quantidade":1}]}}
            """)
    void payloadIncompletoEInvalido(String tipo, String payload) throws Exception {
        var envelope = envelope(tipo, payload);

        assertThatThrownBy(() -> mapeador.mapear(envelope)).isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining(tipo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagamento.confirmado", "pagamento.estornado", "diagnostico.concluido"})
    void payloadNuloEmEventoComCamposObrigatoriosEInvalido(String tipo) throws Exception {
        var envelope = envelope(tipo, null);

        assertThatThrownBy(() -> mapeador.mapear(envelope)).isInstanceOf(MensagemInvalidaException.class);
    }
}
