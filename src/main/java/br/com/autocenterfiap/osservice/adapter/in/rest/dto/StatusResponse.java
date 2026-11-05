package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import java.time.Instant;

public record StatusResponse(Long id, Long numero, String status, Instant atualizadaEm) {

    public static StatusResponse de(OrdemServico os) {
        return new StatusResponse(os.getId(), os.getNumero(), os.getStatus().name(), os.getAtualizadaEm());
    }
}
