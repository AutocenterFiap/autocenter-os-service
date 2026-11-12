package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.application.port.Pagina;
import java.util.List;
import java.util.function.Function;

public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Pagina<E> pagina, Function<E, T> conversor) {
        return new PaginaResponse<>(pagina.conteudo().stream().map(conversor).toList(), pagina.pagina(),
                pagina.tamanho(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
