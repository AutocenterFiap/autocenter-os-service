package br.com.autocenterfiap.osservice.domain.saga;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.time.Instant;

public record RegistroHistorico(Long osId, StatusOS statusAnterior, StatusOS statusNovo, String evento,
        String motivo, Instant data) {
}
