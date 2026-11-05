package br.com.autocenterfiap.osservice.infrastructure.outbox;

import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PublicadorOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorOutbox.class);
    private static final long TIMEOUT_ENVIO_SEGUNDOS = 5;

    private final OutboxJpaRepository repositorio;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PublicadorOutbox(OutboxJpaRepository repositorio, KafkaTemplate<String, String> kafkaTemplate) {
        this.repositorio = repositorio;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public synchronized void publicarPendentes() {
        for (var mensagem : repositorio.findTop100ByEnviadoEmIsNullOrderByIdAsc()) {
            try {
                kafkaTemplate.send(mensagem.getTopico(), mensagem.getChave(), mensagem.getConteudo())
                        .get(TIMEOUT_ENVIO_SEGUNDOS, TimeUnit.SECONDS);
                repositorio.marcarEnviada(mensagem.getId(), Instant.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Publicacao do outbox interrompida na mensagem {}", mensagem.getId());
                return;
            } catch (Exception e) {
                log.error("Falha ao publicar a mensagem {} do outbox; lote encerrado", mensagem.getId(), e);
                return;
            }
        }
    }
}
