package br.com.autocenterfiap.osservice.application.port;

import br.com.autocenterfiap.osservice.application.mensagem.MensagemSaida;

public interface OutboxPort {

    void guardar(MensagemSaida mensagem);
}
