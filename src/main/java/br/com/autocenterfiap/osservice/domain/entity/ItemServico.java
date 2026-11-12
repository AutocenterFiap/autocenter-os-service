package br.com.autocenterfiap.osservice.domain.entity;

import java.math.BigDecimal;

public record ItemServico(Long servicoId, String descricao, BigDecimal valor) {}
