package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Corpo padrão das respostas de erro")
public record ErroResponse(
        @Schema(description = "Código do erro", example = "nao_encontrado") String erro,
        @Schema(description = "Mensagem legível do erro", example = "Ordem de servico nao encontrada: 42")
        String mensagem) {
}
