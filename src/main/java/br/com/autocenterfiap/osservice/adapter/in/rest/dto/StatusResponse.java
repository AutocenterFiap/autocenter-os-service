package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Resumo do status atual da OS")
public record StatusResponse(
        @Schema(description = "Id interno da OS", example = "15") Long id,
        @Schema(description = "Numero da OS mostrado ao cliente", example = "15") Long numero,
        @Schema(description = "Status atual da OS na Saga", implementation = StatusOS.class, example = "CONCLUIDA")
        String status,
        @Schema(description = "Data e hora da ultima mudanca (UTC)", example = "2026-11-06T15:24:25.763Z")
        Instant atualizadaEm) {

    public static StatusResponse de(OrdemServico os) {
        return new StatusResponse(os.getId(), os.getNumero(), os.getStatus().name(), os.getAtualizadaEm());
    }
}
