package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.application.port.Pagina;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

@Schema(description = "Pagina de resultados")
public record PaginaResponse<T>(
        @Schema(description = "Itens desta pagina") List<T> conteudo,
        @Schema(description = "Numero da pagina, comecando em 0", example = "0") int pagina,
        @Schema(description = "Tamanho de pagina efetivamente usado (1 a 100)", example = "20") int tamanho,
        @Schema(description = "Total de registros que atendem ao filtro", example = "3") long totalElementos,
        @Schema(description = "Total de paginas", example = "1") int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Pagina<E> pagina, Function<E, T> conversor) {
        return new PaginaResponse<>(pagina.conteudo().stream().map(conversor).toList(), pagina.pagina(),
                pagina.tamanho(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
