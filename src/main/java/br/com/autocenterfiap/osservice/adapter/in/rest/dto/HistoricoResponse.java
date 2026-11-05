package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import java.time.Instant;

public record HistoricoResponse(String statusAnterior, String statusNovo, String evento, String motivo,
        Instant data) {

    public static HistoricoResponse de(RegistroHistorico r) {
        return new HistoricoResponse(r.statusAnterior() == null ? null : r.statusAnterior().name(),
                r.statusNovo().name(), r.evento(), r.motivo(), r.data());
    }
}
