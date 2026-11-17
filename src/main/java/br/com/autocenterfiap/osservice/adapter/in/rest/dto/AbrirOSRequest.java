package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para abertura de uma ordem de serviço")
public record AbrirOSRequest(
        @Schema(description = "Identificador do cliente no serviço de Cadastro", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "clienteId e obrigatorio") Long clienteId,
        @Schema(description = "Identificador do veículo do cliente no serviço de Cadastro", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "veiculoId e obrigatorio") Long veiculoId,
        @Schema(description = "Problema relatado pelo cliente na recepção", example = "Barulho ao frear",
                maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "problemaRelatado e obrigatorio")
        @Size(max = 500, message = "problemaRelatado deve ter no maximo 500 caracteres") String problemaRelatado) {
}
