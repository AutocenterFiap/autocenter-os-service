package br.com.autocenterfiap.osservice.application.exception;

public class CadastroIndisponivelException extends RuntimeException {

    public CadastroIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
