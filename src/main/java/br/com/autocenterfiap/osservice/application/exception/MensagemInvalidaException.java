package br.com.autocenterfiap.osservice.application.exception;

public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}
