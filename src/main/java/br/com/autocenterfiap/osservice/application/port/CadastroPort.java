package br.com.autocenterfiap.osservice.application.port;

public interface CadastroPort {

    boolean clienteExiste(Long clienteId);

    boolean veiculoExiste(Long veiculoId);
}
