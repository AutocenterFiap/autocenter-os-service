package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Registro do historico da OS: uma mudanca de status ou um evento relevante da Saga")
public record HistoricoResponse(
        @Schema(description = "Status antes do registro. null no primeiro registro (abertura)",
                implementation = StatusOS.class, example = "AGUARDANDO_APROVACAO", nullable = true)
        String statusAnterior,
        @Schema(description = "Status depois do registro (igual ao anterior quando o evento nao muda o status)",
                implementation = StatusOS.class, example = "AGUARDANDO_PAGAMENTO") String statusNovo,
        @Schema(description = "Tipo da mensagem ou acao que gerou o registro", example = "orcamento.aprovado")
        String evento,
        @Schema(description = "Motivo, quando houver (ex.: DIAGNOSTICO_SEM_ITENS, ORCAMENTO_REPROVADO, "
                + "LINK_EXPIRADO, PAGAMENTO_RECUSADO_LIMITE, EXECUCAO_FALHOU, CANCELAMENTO_SOLICITADO)",
                example = "LINK_EXPIRADO", nullable = true) String motivo,
        @Schema(description = "Data e hora do registro (UTC)", example = "2026-11-06T15:24:25.763Z") Instant data) {

    public static HistoricoResponse de(RegistroHistorico r) {
        return new HistoricoResponse(r.statusAnterior() == null ? null : r.statusAnterior().name(),
                r.statusNovo().name(), r.evento(), r.motivo(), r.data());
    }
}
