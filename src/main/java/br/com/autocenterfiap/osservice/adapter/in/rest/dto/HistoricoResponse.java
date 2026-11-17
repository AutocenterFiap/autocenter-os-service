package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Transição de status registrada no histórico da OS")
public record HistoricoResponse(
        @Schema(description = "Status antes da transição; nulo na abertura", example = "AGUARDANDO_PAGAMENTO")
        String statusAnterior,
        @Schema(description = "Status após a transição", example = "EM_EXECUCAO") String statusNovo,
        @Schema(description = "Evento que provocou a transição", example = "pagamento.aprovado") String evento,
        @Schema(description = "Motivo informado em recusas, falhas ou cancelamentos", example = "Cartao recusado")
        String motivo,
        @Schema(description = "Data da transição", example = "2026-11-17T12:30:00Z") Instant data) {

    public static HistoricoResponse de(RegistroHistorico r) {
        return new HistoricoResponse(r.statusAnterior() == null ? null : r.statusAnterior().name(),
                r.statusNovo().name(), r.evento(), r.motivo(), r.data());
    }
}
