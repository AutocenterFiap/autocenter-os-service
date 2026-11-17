package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ordem de serviço")
public record OSResponse(
        @Schema(description = "Identificador interno da OS", example = "42") Long id,
        @Schema(description = "Número sequencial da OS apresentado ao cliente", example = "1001") Long numero,
        @Schema(description = "Identificador do cliente", example = "1") Long clienteId,
        @Schema(description = "Identificador do veículo", example = "1") Long veiculoId,
        @Schema(description = "Problema relatado na abertura", example = "Barulho ao frear") String problemaRelatado,
        @Schema(description = "Status atual da OS na Saga", example = "AGUARDANDO_APROVACAO",
                allowableValues = {"ABERTA", "EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "AGUARDANDO_PAGAMENTO",
                        "PAGAMENTO_RECUSADO", "EM_EXECUCAO", "AGUARDANDO_ESTORNO", "CONCLUIDA", "ENTREGUE",
                        "ORCAMENTO_REPROVADO", "CANCELADA"}) String status,
        @Schema(description = "Valor total do orçamento", example = "773.90") BigDecimal valorTotal,
        @Schema(description = "Serviços e produtos definidos no diagnóstico") ItensResponse itens,
        @Schema(description = "Link de pagamento gerado pelo Billing; preenchido apenas no detalhe da OS",
                example = "https://pagamento.exemplo/checkout/abc123") String linkPagamento,
        @Schema(description = "Data de abertura", example = "2026-11-17T12:00:00Z") Instant criadaEm,
        @Schema(description = "Data da última alteração", example = "2026-11-17T12:30:00Z") Instant atualizadaEm,
        @Schema(description = "Identificador de correlação da Saga, propagado nos eventos Kafka",
                example = "3f1c2b9e-7a44-4c1e-9d0b-1a2b3c4d5e6f") UUID correlationId,
        @Schema(description = "Indica estorno pendente de confirmação; presente apenas no detalhe da OS",
                example = "false")
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean estornoPendente) {

    public static OSResponse de(OrdemServico os) {
        return montar(os, null, null);
    }

    public static OSResponse detalhe(OrdemServico os, String linkPagamento, boolean estornoPendente) {
        return montar(os, linkPagamento, estornoPendente);
    }

    private static OSResponse montar(OrdemServico os, String linkPagamento, Boolean estornoPendente) {
        return new OSResponse(os.getId(), os.getNumero(), os.getClienteId(), os.getVeiculoId(),
                os.getProblemaRelatado(), os.getStatus().name(), os.getValorTotal(), ItensResponse.de(os.getItens()),
                linkPagamento, os.getCriadaEm(), os.getAtualizadaEm(), os.getCorrelationId(), estornoPendente);
    }
}
