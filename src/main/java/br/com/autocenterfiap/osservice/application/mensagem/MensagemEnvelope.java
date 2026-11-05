package br.com.autocenterfiap.osservice.application.mensagem;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MensagemEnvelope(UUID eventId, String tipo, int versao, Long osId, UUID correlationId,
        Instant ocorridoEm, JsonNode payload) {
}
