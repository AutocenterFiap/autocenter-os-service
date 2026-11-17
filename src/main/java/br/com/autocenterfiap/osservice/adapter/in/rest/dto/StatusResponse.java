package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Situação resumida da OS")
public record StatusResponse(
        @Schema(description = "Identificador interno da OS", example = "42") Long id,
        @Schema(description = "Número sequencial da OS", example = "1001") Long numero,
        @Schema(description = "Status atual da OS", example = "EM_EXECUCAO",
                allowableValues = {"ABERTA", "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "AGUARDANDO_PAGAMENTO",
                        "PAGAMENTO_RECUSADO", "EM_EXECUCAO", "AGUARDANDO_ESTORNO", "CONCLUIDA", "ENTREGUE",
                        "ORCAMENTO_REPROVADO", "CANCELADA"})
        String status,
        @Schema(description = "Data da última alteração", example = "2026-11-17T12:30:00Z") Instant atualizadaEm) {

    public static StatusResponse de(OrdemServico os) {
        return new StatusResponse(os.getId(), os.getNumero(), os.getStatus().name(), os.getAtualizadaEm());
    }
}
