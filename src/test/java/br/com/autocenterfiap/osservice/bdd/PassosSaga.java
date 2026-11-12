package br.com.autocenterfiap.osservice.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.autocenterfiap.osservice.TokensTeste;
import br.com.autocenterfiap.osservice.application.mensagem.Topicos;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;

public class PassosSaga {

    private static final String URL = "/v1/ordem-servicos";
    private static final AtomicLong VEICULOS = new AtomicLong(700_000);
    private static final Duration ESPERA = Duration.ofSeconds(90);
    private static final Duration JANELA_AUSENCIA = Duration.ofSeconds(3);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private DubleParticipantes duble;
    @Autowired
    private SagaRepositoryPort sagas;

    private long osId;

    @Dado("uma OS aberta pela API")
    public void umaOsAbertaPelaApi() {
        var corpo = Map.of("clienteId", 1, "veiculoId", VEICULOS.incrementAndGet(),
                "problemaRelatado", "Barulho no motor");
        var resposta = rest.exchange(URL, HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos()), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        osId = ((Number) resposta.getBody().get("id")).longValue();
    }

    @Dado("uma OS com orçamento gerado e aguardando aprovação")
    public void umaOsComOrcamentoGeradoEAguardandoAprovacao() {
        umaOsAbertaPelaApi();
        aExecucaoIniciaODiagnostico();
        aExecucaoConcluiODiagnosticoComUmServicoEUmaPeca();
        oBillingGeraOOrcamento();
        aOsFicaComStatus("AGUARDANDO_APROVACAO");
    }

    @Dado("uma OS com orçamento aprovado e aguardando pagamento")
    public void umaOsComOrcamentoAprovadoEAguardandoPagamento() {
        umaOsComOrcamentoGeradoEAguardandoAprovacao();
        oClienteAprovaOOrcamento();
        aOsFicaComStatus("AGUARDANDO_PAGAMENTO");
        oBillingRecebeOComando("billing.gerar-cobranca");
    }

    @Dado("uma OS com pagamento confirmado e em execução")
    public void umaOsComPagamentoConfirmadoEEmExecucao() {
        umaOsComOrcamentoAprovadoEAguardandoPagamento();
        oBillingGeraOLinkDePagamentoDaTentativa(1);
        oPagamentoDaTentativaEConfirmado(1);
        aOsFicaComStatus("EM_EXECUCAO");
    }

    @Quando("a Execução inicia o diagnóstico")
    public void aExecucaoIniciaODiagnostico() {
        publicar(Topicos.EXECUCAO_EVENTOS, "diagnostico.iniciado", Map.of("mecanicoId", 3));
        aOsFicaComStatus("EM_DIAGNOSTICO");
    }

    @Quando("a Execução conclui o diagnóstico com um serviço e uma peça")
    public void aExecucaoConcluiODiagnosticoComUmServicoEUmaPeca() {
        var servico = Map.of("servicoId", 1, "descricao", "Troca de oleo", "valor", "120.00");
        var produto = Map.of("produtoId", 7, "descricao", "Filtro", "quantidade", 2, "precoUnitario", "45.00");
        publicar(Topicos.EXECUCAO_EVENTOS, "diagnostico.concluido",
                Map.of("itens", Map.of("servicos", List.of(servico), "produtos", List.of(produto))));
    }

    @Quando("o Billing gera o orçamento")
    public void oBillingGeraOOrcamento() {
        oBillingRecebeOComando("billing.gerar-orcamento");
        publicar(Topicos.BILLING_EVENTOS, "orcamento.gerado", Map.of("orcamentoId", osId, "valorTotal", "210.00"));
    }

    @Quando("o cliente aprova o orçamento")
    public void oClienteAprovaOOrcamento() {
        publicar(Topicos.BILLING_EVENTOS, "orcamento.aprovado", Map.of("orcamentoId", osId));
    }

    @Quando("o cliente reprova o orçamento")
    public void oClienteReprovaOOrcamento() {
        publicar(Topicos.BILLING_EVENTOS, "orcamento.reprovado", Map.of("orcamentoId", osId, "motivo", "Caro"));
    }

    @Quando("o Billing gera o link de pagamento da tentativa {int}")
    public void oBillingGeraOLinkDePagamentoDaTentativa(Integer tentativa) {
        esperarComandos("billing.gerar-cobranca", tentativa);
        publicar(Topicos.BILLING_EVENTOS, "pagamento.link-gerado", Map.of("pagamentoId", pagamentoId(tentativa),
                "url", "http://pague/" + osId + "/" + tentativa, "tentativa", tentativa,
                "expiraEm", Instant.now().plusSeconds(1800).toString()));
        await().atMost(ESPERA).untilAsserted(() -> assertThat(sagas.buscar(osId).getLinkPagamento())
                .isEqualTo("http://pague/" + osId + "/" + tentativa));
    }

    @Quando("o pagamento da tentativa {int} é confirmado")
    public void oPagamentoDaTentativaEConfirmado(Integer tentativa) {
        publicar(Topicos.BILLING_EVENTOS, "pagamento.confirmado",
                Map.of("pagamentoId", pagamentoId(tentativa), "valor", "210.00"));
    }

    @Quando("o pagamento da tentativa {int} é recusado")
    public void oPagamentoDaTentativaERecusado(Integer tentativa) {
        publicar(Topicos.BILLING_EVENTOS, "pagamento.recusado",
                Map.of("pagamentoId", pagamentoId(tentativa), "tentativa", tentativa, "motivo", "Saldo insuficiente"));
        if (tentativa < 3) {
            aOsFicaComStatus("PAGAMENTO_RECUSADO");
        }
    }

    @Quando("a Execução inicia o reparo")
    public void aExecucaoIniciaOReparo() {
        publicar(Topicos.EXECUCAO_EVENTOS, "execucao.iniciada", Map.of("mecanicoId", 3));
    }

    @Quando("a Execução finaliza o reparo")
    public void aExecucaoFinalizaOReparo() {
        publicar(Topicos.EXECUCAO_EVENTOS, "execucao.finalizada", Map.of());
    }

    @Quando("a Execução informa falha no reparo")
    public void aExecucaoInformaFalhaNoReparo() {
        publicar(Topicos.EXECUCAO_EVENTOS, "execucao.falhou", Map.of("motivo", "Peca defeituosa"));
    }

    @Quando("o Billing confirma o estorno")
    public void oBillingConfirmaOEstorno() {
        var comando = duble.mensagens(osId, "billing.estornar-pagamento").get(0);
        publicar(Topicos.BILLING_EVENTOS, "pagamento.estornado",
                Map.of("pagamentoId", comando.get("payload").get("pagamentoId").asLong()));
    }

    @Quando("a entrega é registrada pela API")
    public void aEntregaERegistradaPelaApi() {
        acionar("entregar");
    }

    @Quando("o cancelamento é solicitado pela API")
    public void oCancelamentoESolicitadoPelaApi() {
        acionar("cancelar");
    }

    @Quando("o prazo de pagamento vence")
    public void oPrazoDePagamentoVence() {
        var saga = sagas.buscar(osId);
        saga.setPrazoPagamento(Instant.now().minusSeconds(60));
        sagas.salvar(saga);
    }

    @Entao("a OS fica com status {string}")
    public void aOsFicaComStatus(String esperado) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(statusAtual()).isEqualTo(esperado));
    }

    @E("a OS permanece com status {string}")
    public void aOsPermaneceComStatus(String esperado) {
        await().during(JANELA_AUSENCIA).atMost(ESPERA).untilAsserted(() -> assertThat(statusAtual()).isEqualTo(esperado));
    }

    @Entao("o Billing recebe o comando {string}")
    public void oBillingRecebeOComando(String tipo) {
        esperarComandos(tipo, 1);
    }

    @E("o Billing recebe {int} comandos {string}")
    public void oBillingRecebeNComandos(Integer quantidade, String tipo) {
        esperarComandos(tipo, quantidade);
        await().during(JANELA_AUSENCIA).atMost(ESPERA)
                .untilAsserted(() -> assertThat(duble.mensagens(osId, tipo)).hasSize(quantidade));
    }

    @E("o Billing não recebe o comando {string}")
    public void oBillingNaoRecebeOComando(String tipo) {
        naoRecebeu(tipo);
    }

    @Entao("a Execução recebe o comando {string}")
    public void aExecucaoRecebeOComando(String tipo) {
        esperarComandos(tipo, 1);
    }

    @E("a Execução não recebe o comando {string}")
    public void aExecucaoNaoRecebeOComando(String tipo) {
        naoRecebeu(tipo);
    }

    @Entao("o OS Service publica o evento {string}")
    public void oOsServicePublicaOEvento(String tipo) {
        esperarComandos(tipo, 1);
    }

    @E("o OS Service publica o evento {string} com motivo {string}")
    public void oOsServicePublicaOEventoComMotivo(String tipo, String motivo) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(duble.mensagens(osId, tipo))
                .anySatisfy(m -> assertThat(m.get("payload").get("motivo").asText()).isEqualTo(motivo)));
    }

    @E("o OS Service não publica o evento {string}")
    public void oOsServiceNaoPublicaOEvento(String tipo) {
        naoRecebeu(tipo);
    }

    private long pagamentoId(int tentativa) {
        return osId * 10 + tentativa;
    }

    private String statusAtual() {
        var resposta = rest.exchange(URL + "/status/" + osId, HttpMethod.GET, new HttpEntity<>(cabecalhos()),
                Map.class);
        return (String) resposta.getBody().get("status");
    }

    private void acionar(String acao) {
        var resposta = rest.exchange(URL + "/" + osId + "/" + acao, HttpMethod.POST,
                new HttpEntity<>(cabecalhos()), Map.class);
        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private HttpHeaders cabecalhos() {
        var cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(TokensTeste.administrativo());
        return cabecalhos;
    }

    private void esperarComandos(String tipo, int minimo) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(duble.mensagens(osId, tipo)).hasSizeGreaterThanOrEqualTo(minimo));
    }

    private void naoRecebeu(String tipo) {
        await().during(JANELA_AUSENCIA).atMost(ESPERA).untilAsserted(() -> assertThat(duble.mensagens(osId, tipo)).isEmpty());
    }

    private void publicar(String topico, String tipo, Map<String, ?> payload) {
        var envelope = mapper.createObjectNode();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("osId", osId);
        envelope.put("correlationId", UUID.randomUUID().toString());
        envelope.put("ocorridoEm", Instant.now().toString());
        envelope.set("payload", mapper.valueToTree(payload));
        try {
            kafka.send(topico, String.valueOf(osId), mapper.writeValueAsString(envelope)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar " + tipo, e);
        }
    }
}
