package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import java.math.BigDecimal;
import java.time.Instant;

public record OSResponse(Long id, Long numero, Long clienteId, Long veiculoId, String problemaRelatado,
        String status, BigDecimal valorTotal, ItensOS itens, String linkPagamento, Instant criadaEm,
        Instant atualizadaEm) {

    public static OSResponse de(OrdemServico os, String linkPagamento) {
        return new OSResponse(os.getId(), os.getNumero(), os.getClienteId(), os.getVeiculoId(),
                os.getProblemaRelatado(), os.getStatus().name(), os.getValorTotal(), os.getItens(),
                linkPagamento, os.getCriadaEm(), os.getAtualizadaEm());
    }
}
