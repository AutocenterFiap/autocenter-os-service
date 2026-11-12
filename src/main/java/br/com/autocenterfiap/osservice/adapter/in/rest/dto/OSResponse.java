package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OSResponse(Long id, Long numero, Long clienteId, Long veiculoId, String problemaRelatado,
        String status, BigDecimal valorTotal, ItensOS itens, String linkPagamento, Instant criadaEm,
        Instant atualizadaEm, UUID correlationId,
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
