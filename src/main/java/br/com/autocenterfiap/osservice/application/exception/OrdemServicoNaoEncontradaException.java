package br.com.autocenterfiap.osservice.application.exception;

public class OrdemServicoNaoEncontradaException extends RuntimeException {

    public OrdemServicoNaoEncontradaException(Long id) {
        super("Ordem de servico nao encontrada: " + id);
    }
}
