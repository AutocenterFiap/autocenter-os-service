package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.application.port.Pagina;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

@Schema(description = "Página de resultados")
public record PaginaResponse<T>(
        @Schema(description = "Itens da página") List<T> conteudo,
        @Schema(description = "Número da página, iniciando em 0", example = "0") int pagina,
        @Schema(description = "Quantidade de itens por página", example = "20") int tamanho,
        @Schema(description = "Total de itens encontrados", example = "57") long totalElementos,
        @Schema(description = "Total de páginas", example = "3") int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Pagina<E> pagina, Function<E, T> conversor) {
        return new PaginaResponse<>(pagina.conteudo().stream().map(conversor).toList(), pagina.pagina(),
                pagina.tamanho(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
