package br.com.autocenterfiap.osservice.application.port;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.time.Duration;

public interface ObservabilidadePort {

    void osCriada();

    void tempoNoStatus(StatusOS status, Duration tempo);

    void mudancaStatus(StatusOS novoStatus);

    void sagaCompensada(String motivo);

    void eventoDescartado(String tipo);

    void estornoFalhou();
}
