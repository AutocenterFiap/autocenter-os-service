package br.com.autocenterfiap.osservice.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSaida;
import br.com.autocenterfiap.osservice.application.mensagem.Topicos;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.KafkaContainer;

@SpringBootTest(properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class PublicadorOutboxIT {

    @Autowired
    private OutboxPort outbox;
    @Autowired
    private PublicadorOutbox publicador;
    @Autowired
    private OutboxJpaRepository repositorio;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private KafkaContainer kafka;

    @Test
    void publicaComChaveOsId() throws Exception {
        var eventId = UUID.randomUUID();
        var envelope = new MensagemEnvelope(eventId, "os.concluida", 1, 123L, UUID.randomUUID(),
                Instant.parse("2026-11-05T14:30:00Z"), mapper.createObjectNode());
        outbox.guardar(new MensagemSaida(Topicos.OS_EVENTOS, envelope));

        publicador.publicarPendentes();

        try (var consumidor = new KafkaConsumer<String, String>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumidor.subscribe(List.of(Topicos.OS_EVENTOS));
            var fim = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < fim) {
                for (var registro : consumidor.poll(Duration.ofMillis(500))) {
                    var lido = mapper.readValue(registro.value(), MensagemEnvelope.class);
                    if (eventId.equals(lido.eventId())) {
                        assertThat(registro.key()).isEqualTo("123");
                        assertThat(lido.osId()).isEqualTo(123L);
                        assertThat(lido.tipo()).isEqualTo("os.concluida");
                        assertThat(lido.ocorridoEm()).isEqualTo(Instant.parse("2026-11-05T14:30:00Z"));
                        assertThat(repositorio.findTop100ByEnviadoEmIsNullOrderByIdAsc())
                                .noneMatch(m -> m.getEventId().equals(eventId.toString()));
                        return;
                    }
                }
            }
            throw new AssertionError("Mensagem nao chegou ao Kafka");
        }
    }
}
