package br.com.autocenterfiap.osservice.application.exception;

public class CadastroNaoEncontradoException extends RuntimeException {

    public CadastroNaoEncontradoException(String recurso, Long id) {
        super(recurso + " nao encontrado: " + id);
    }
}
