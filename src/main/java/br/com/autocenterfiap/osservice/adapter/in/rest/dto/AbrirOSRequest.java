package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para abrir uma ordem de servico e iniciar a Saga")
public record AbrirOSRequest(
        @Schema(description = "Id do cliente no Cadastro. Precisa existir (no modo stub, qualquer valor e aceito)",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "clienteId e obrigatorio") Long clienteId,
        @Schema(description = "Id do veiculo no Cadastro. O veiculo nao pode ter outra OS em andamento "
                + "(ate EM_EXECUCAO)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "veiculoId e obrigatorio") Long veiculoId,
        @Schema(description = "Problema descrito pelo cliente na recepcao (1 a 500 caracteres, nao pode ser so "
                + "espacos)", example = "Barulho na suspensao dianteira ao passar em lombadas",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
        @NotBlank(message = "problemaRelatado e obrigatorio")
        @Size(max = 500, message = "problemaRelatado deve ter no maximo 500 caracteres") String problemaRelatado) {
}
