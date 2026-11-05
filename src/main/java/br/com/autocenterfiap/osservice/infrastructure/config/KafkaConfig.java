package br.com.autocenterfiap.osservice.infrastructure.config;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaConfig {

    private static final String SUFIXO_DLT = ".dlt";
    private static final int MAX_RETENTATIVAS = 3;
    private static final long INTERVALO_INICIAL_MS = 1000L;
    private static final double MULTIPLICADOR = 2.0;

    @Bean
    DefaultErrorHandler tratadorDeErrosKafka(KafkaOperations<?, ?> template) {
        var recuperador = new DeadLetterPublishingRecoverer(template,
                (registro, erro) -> new TopicPartition(registro.topic() + SUFIXO_DLT, registro.partition()));
        var tratador = new DefaultErrorHandler(recuperador, intervaloDeRetentativas());
        tratador.addNotRetryableExceptions(JsonProcessingException.class, MensagemInvalidaException.class);
        return tratador;
    }

    static ExponentialBackOffWithMaxRetries intervaloDeRetentativas() {
        var backOff = new ExponentialBackOffWithMaxRetries(MAX_RETENTATIVAS);
        backOff.setInitialInterval(INTERVALO_INICIAL_MS);
        backOff.setMultiplier(MULTIPLICADOR);
        return backOff;
    }
}
