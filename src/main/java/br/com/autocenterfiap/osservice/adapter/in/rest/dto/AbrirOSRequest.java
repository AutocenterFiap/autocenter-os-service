package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AbrirOSRequest(
        @NotNull(message = "clienteId e obrigatorio") Long clienteId,
        @NotNull(message = "veiculoId e obrigatorio") Long veiculoId,
        @NotBlank(message = "problemaRelatado e obrigatorio")
        @Size(max = 500, message = "problemaRelatado deve ter no maximo 500 caracteres") String problemaRelatado) {
}
