package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Corpo de erro padrao")
public record ErroResponse(
        @Schema(description = "Codigo do erro: requisicao_invalida, nao_autenticado, acesso_negado, nao_encontrado, "
                + "os_em_andamento, acao_recusada ou conflito_concorrencia", example = "acao_recusada") String erro,
        @Schema(description = "Mensagem legivel para quem chamou",
                example = "So e possivel entregar uma OS concluida") String mensagem) {
}
