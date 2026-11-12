package br.com.autocenterfiap.osservice.application.port;

import br.com.autocenterfiap.osservice.application.mensagem.EntradaSaga;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import java.util.Optional;

public interface MapeadorGatilhoPort {

    Optional<EntradaSaga> mapear(MensagemEnvelope mensagem);
}
