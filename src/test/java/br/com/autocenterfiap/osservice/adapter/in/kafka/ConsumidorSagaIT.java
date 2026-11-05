package br.com.autocenterfiap.osservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.application.mensagem.Topicos;
import br.com.autocenterfiap.osservice.application.usecase.AbrirOrdemServicoUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;

@SpringBootTest(properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class ConsumidorSagaIT {

    private static final Duration ESPERA = Duration.ofSeconds(60);
    private static final AtomicLong VEICULOS = new AtomicLong(600_000);

    @Autowired
    private AbrirOrdemServicoUseCase abrir;
    @Autowired
    private KafkaTemplate<String, String> template;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private KafkaContainer kafka;
    @Autowired
    private MeterRegistry metricas;

    private long abrirOS() {
        return abrir.executar(1L, VEICULOS.incrementAndGet(), "Barulho no motor").getId();
    }

    private String envelope(String eventId, String tipo, Long osId, Map<String, ?> payload) throws Exception {
        var envelope = mapper.createObjectNode();
        envelope.put("eventId", eventId);
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("osId", osId);
        envelope.put("correlationId", UUID.randomUUID().toString());
        envelope.put("ocorridoEm", "2026-11-05T14:30:00Z");
        envelope.set("payload", mapper.valueToTree(payload));
        return mapper.writeValueAsString(envelope);
    }

    private void publicar(String topico, long chave, String conteudo) throws Exception {
        template.send(topico, String.valueOf(chave), conteudo).get();
    }

    private void publicarEvento(String topico, long osId, String tipo, Map<String, ?> payload) throws Exception {
        publicar(topico, osId, envelope(UUID.randomUUID().toString(), tipo, osId, payload));
    }

    private String status(long osId) {
        return jdbc.queryForObject("select status from ordem_servico where id = ?", String.class, osId);
    }

    private void esperarStatus(long osId, String esperado) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(status(osId)).isEqualTo(esperado));
    }

    private void colocarEm(long osId, String status) {
        jdbc.update("update ordem_servico set status = ? where id = ?", status, osId);
    }

    private long linhasDeHistorico(long osId) {
        return jdbc.queryForObject("select count(*) from os_historico where os_id = ?", Long.class, osId);
    }

    private double descartes(String tipo) {
        var contador = metricas.find("autocenter.saga.evento_descartado.total").tag("tipo", tipo).counter();
        return contador == null ? 0 : contador.count();
    }

    private void esperarNaDlt(String topicoDlt, String conteudo) {
        try (var consumidor = new KafkaConsumer<String, String>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumidor.subscribe(List.of(topicoDlt));
            var fim = System.currentTimeMillis() + ESPERA.toMillis();
            while (System.currentTimeMillis() < fim) {
                for (var registro : consumidor.poll(Duration.ofMillis(500))) {
                    if (conteudo.equals(registro.value())) {
                        return;
                    }
                }
            }
            throw new AssertionError("Mensagem nao chegou a " + topicoDlt);
        }
    }

    @Test
    void eventoAvancaAOS() throws Exception {
        var osId = abrirOS();

        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, "diagnostico.iniciado", Map.of("mecanicoId", 3));

        esperarStatus(osId, "EM_DIAGNOSTICO");
        assertThat(linhasDeHistorico(osId)).isEqualTo(2);
    }

    @Test
    void mesmoEventIdDuasVezesProcessaUmaSo() throws Exception {
        var osId = abrirOS();
        colocarEm(osId, "EM_EXECUCAO");
        var repetida = envelope(UUID.randomUUID().toString(), "execucao.iniciada", osId, Map.of());

        publicar(Topicos.EXECUCAO_EVENTOS, osId, repetida);
        publicar(Topicos.EXECUCAO_EVENTOS, osId, repetida);
        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, "execucao.finalizada", Map.of());

        esperarStatus(osId, "CONCLUIDA");
        assertThat(jdbc.queryForObject("select count(*) from os_historico where os_id = ? and evento = ?",
                Long.class, osId, "execucao.iniciada")).isEqualTo(1);
    }

    @Test
    void erroTecnicoRepeteVaiParaDltEDesfazTudoDaTransacao() throws Exception {
        var osId = abrirOS();
        colocarEm(osId, "PAGAMENTO_RECUSADO");
        var eventId = UUID.randomUUID().toString();
        var mensagem = envelope(eventId, "pagamento.link-gerado", osId,
                Map.of("pagamentoId", 77, "url", "http://pague/" + "x".repeat(1100), "tentativa", 1));

        publicar(Topicos.BILLING_EVENTOS, osId, mensagem);

        esperarNaDlt("billing.eventos.dlt", mensagem);
        assertThat(status(osId)).isEqualTo("PAGAMENTO_RECUSADO");
        assertThat(linhasDeHistorico(osId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from mensagem_processada where event_id = ?", Long.class,
                eventId)).isZero();
    }

    @Test
    void osInexistenteEDescartadaEAFilaContinua() throws Exception {
        var osId = abrirOS();
        var antes = descartes("execucao.iniciada");

        publicar(Topicos.EXECUCAO_EVENTOS, osId,
                envelope(UUID.randomUUID().toString(), "execucao.iniciada", 999_999_999L, Map.of()));
        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, "diagnostico.iniciado", Map.of());

        esperarStatus(osId, "EM_DIAGNOSTICO");
        assertThat(descartes("execucao.iniciada")).isEqualTo(antes + 1);
    }

    @Test
    void jsonInvalidoVaiParaDltSemBloquear() throws Exception {
        var osId = abrirOS();
        colocarEm(osId, "EM_DIAGNOSTICO");
        var invalido = "isto nao e json " + UUID.randomUUID();

        publicar(Topicos.BILLING_EVENTOS, osId, invalido);
        publicarEvento(Topicos.BILLING_EVENTOS, osId, "orcamento.gerado", Map.of("orcamentoId", 55));

        esperarNaDlt("billing.eventos.dlt", invalido);
        esperarStatus(osId, "AGUARDANDO_APROVACAO");
    }

    @Test
    void payloadIncompletoVaiParaDltSemGatilhoComIdNulo() throws Exception {
        var osId = abrirOS();
        var incompleta = envelope(UUID.randomUUID().toString(), "pagamento.confirmado", osId,
                Map.of("valor", "10.00"));

        publicar(Topicos.BILLING_EVENTOS, osId, incompleta);

        esperarNaDlt("billing.eventos.dlt", incompleta);
        assertThat(status(osId)).isEqualTo("ABERTA");
        assertThat(jdbc.queryForObject("select count(*) from mensagem_processada where event_id = ?", Long.class,
                mapper.readTree(incompleta).get("eventId").asText())).isZero();
    }

    @Test
    void tipoDesconhecidoEDescartado() throws Exception {
        var osId = abrirOS();
        var tipo = "evento.novo";
        var antes = descartes(tipo);

        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, tipo, Map.of());
        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, "diagnostico.iniciado", Map.of());

        esperarStatus(osId, "EM_DIAGNOSTICO");
        assertThat(descartes(tipo)).isEqualTo(antes + 1);
    }

    @Test
    void fluxoAtePagamentoPreservaItensEGravaCobrancaNoOutbox() throws Exception {
        var osId = abrirOS();
        var servico = Map.of("servicoId", 1, "descricao", "Troca de oleo", "valor", "120.00");
        var produto = Map.of("produtoId", 7, "descricao", "Filtro", "quantidade", 2, "precoUnitario", "45.00");

        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, "diagnostico.iniciado", Map.of());
        publicarEvento(Topicos.EXECUCAO_EVENTOS, osId, "diagnostico.concluido",
                Map.of("itens", Map.of("servicos", List.of(servico), "produtos", List.of(produto))));
        await().atMost(ESPERA).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select valor_total from ordem_servico where id = ?", BigDecimal.class, osId))
                .isEqualByComparingTo("210.00"));
        publicarEvento(Topicos.BILLING_EVENTOS, osId, "orcamento.gerado", Map.of("orcamentoId", 55));
        esperarStatus(osId, "AGUARDANDO_APROVACAO");
        publicarEvento(Topicos.BILLING_EVENTOS, osId, "orcamento.aprovado", Map.of("orcamentoId", 55));
        esperarStatus(osId, "AGUARDANDO_PAGAMENTO");
        publicarEvento(Topicos.BILLING_EVENTOS, osId, "pagamento.link-gerado",
                Map.of("pagamentoId", 77, "url", "http://pague/1", "tentativa", 1));
        await().atMost(ESPERA).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select link_pagamento from saga_os where os_id = ?", String.class, osId))
                .isEqualTo("http://pague/1"));
        publicarEvento(Topicos.BILLING_EVENTOS, osId, "pagamento.confirmado", Map.of("pagamentoId", 77));

        esperarStatus(osId, "EM_EXECUCAO");
        var saga = jdbc.queryForMap("select * from saga_os where os_id = ?", osId);
        assertThat(saga.get("orcamento_id")).isEqualTo(55L);
        assertThat(saga.get("pagamento_id")).isEqualTo(77L);
        assertThat(saga.get("pagamento_confirmado_id")).isEqualTo(77L);
        assertThat(saga.get("tentativas_pagamento")).isEqualTo(1);
        assertThat(saga.get("prazo_pagamento")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from os_item_servico where os_id = ?", Long.class, osId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from os_item_produto where os_id = ?", Long.class, osId))
                .isEqualTo(1);
        var comandos = jdbc.queryForList("select conteudo from outbox where chave = ? order by id", String.class,
                String.valueOf(osId));
        assertThat(comandos).hasSize(4);
        assertThat(comandos.get(3)).contains("execucao.iniciar-reparo").contains("Troca de oleo");
        assertThat(jdbc.queryForObject("select veiculo_ativo_id is not null from ordem_servico where id = ?",
                Boolean.class, osId)).isTrue();
    }
}
