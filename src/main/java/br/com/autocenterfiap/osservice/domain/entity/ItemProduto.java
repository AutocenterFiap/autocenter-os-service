package br.com.autocenterfiap.osservice.domain.entity;

import java.math.BigDecimal;

public record ItemProduto(Long produtoId, String descricao, int quantidade, BigDecimal precoUnitario) {}
