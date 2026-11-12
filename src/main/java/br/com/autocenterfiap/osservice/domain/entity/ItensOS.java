package br.com.autocenterfiap.osservice.domain.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record ItensOS(List<ItemServico> servicos, List<ItemProduto> produtos) {

    public static final ItensOS VAZIO = new ItensOS(List.of(), List.of());

    public ItensOS {
        servicos = servicos == null ? List.of() : List.copyOf(servicos);
        produtos = produtos == null ? List.of() : List.copyOf(produtos);
    }

    public boolean vazio() {
        return servicos.isEmpty() && produtos.isEmpty();
    }

    public BigDecimal total() {
        var totalServicos = servicos.stream().map(ItemServico::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalProdutos = produtos.stream()
                .map(p -> p.precoUnitario().multiply(BigDecimal.valueOf(p.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalServicos.add(totalProdutos).setScale(2, RoundingMode.HALF_UP);
    }
}
