package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ordem de servico")
public record OSResponse(
        @Schema(description = "Id interno da OS", example = "15") Long id,
        @Schema(description = "Numero da OS mostrado ao cliente (sequencial)", example = "15") Long numero,
        @Schema(description = "Id do cliente no Cadastro", example = "1") Long clienteId,
        @Schema(description = "Id do veiculo no Cadastro", example = "42") Long veiculoId,
        @Schema(description = "Problema descrito na abertura",
                example = "Barulho na suspensao dianteira ao passar em lombadas") String problemaRelatado,
        @Schema(description = "Status atual da OS na Saga", implementation = StatusOS.class,
                example = "AGUARDANDO_PAGAMENTO") String status,
        @Schema(description = "Soma dos servicos e produtos do diagnostico. 0.00 enquanto o diagnostico nao "
                + "terminar", example = "165.00") BigDecimal valorTotal,
        ItensOS itens,
        @Schema(description = "Link de pagamento atual. Preenchido so no detalhe (GET /{id} e /numero/{numero}) "
                + "e so em AGUARDANDO_PAGAMENTO ou PAGAMENTO_RECUSADO; null nos demais casos",
                example = "https://pagamento.autocenter.local/cobranca/abc123", nullable = true)
        String linkPagamento,
        @Schema(description = "Data e hora de abertura (UTC)", example = "2026-11-06T15:23:54.579Z") Instant criadaEm,
        @Schema(description = "Data e hora da ultima mudanca (UTC)", example = "2026-11-06T15:24:25.763Z")
        Instant atualizadaEm,
        @Schema(description = "Id de correlacao da Saga, presente em todas as mensagens Kafka desta OS",
                example = "c77185ac-30bf-4bd3-b1f6-be1c45cedb8a") UUID correlationId,
        @Schema(description = "true se ha um estorno pendente (ver POST /{id}/reprocessar-estorno). So aparece "
                + "no detalhe; ausente na lista e na abertura", example = "false", nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean estornoPendente) {

    public static OSResponse de(OrdemServico os) {
        return montar(os, null, null);
    }

    public static OSResponse detalhe(OrdemServico os, String linkPagamento, boolean estornoPendente) {
        return montar(os, linkPagamento, estornoPendente);
    }

    private static OSResponse montar(OrdemServico os, String linkPagamento, Boolean estornoPendente) {
        return new OSResponse(os.getId(), os.getNumero(), os.getClienteId(), os.getVeiculoId(),
                os.getProblemaRelatado(), os.getStatus().name(), os.getValorTotal(), os.getItens(),
                linkPagamento, os.getCriadaEm(), os.getAtualizadaEm(), os.getCorrelationId(), estornoPendente);
    }
}
