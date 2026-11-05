package br.com.autocenterfiap.osservice.adapter.in.kafka;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import br.com.autocenterfiap.osservice.application.mensagem.Topicos;
import br.com.autocenterfiap.osservice.application.usecase.ConsumirMensagemUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConsumidorSaga {

    private static final String CHAVE_MDC = "correlationId";

    private final ObjectMapper mapper;
    private final ConsumirMensagemUseCase consumir;

    public ConsumidorSaga(ObjectMapper mapper, ConsumirMensagemUseCase consumir) {
        this.mapper = mapper;
        this.consumir = consumir;
    }

    @KafkaListener(topics = {Topicos.BILLING_EVENTOS, Topicos.EXECUCAO_EVENTOS}, groupId = "os-service")
    public void consumir(String mensagem) throws JsonProcessingException {
        var envelope = mapper.readValue(mensagem, MensagemEnvelope.class);
        if (envelope == null) {
            throw new MensagemInvalidaException("Mensagem sem envelope");
        }
        MDC.put(CHAVE_MDC, Objects.toString(envelope.correlationId(), ""));
        try {
            consumir.executar(envelope);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
