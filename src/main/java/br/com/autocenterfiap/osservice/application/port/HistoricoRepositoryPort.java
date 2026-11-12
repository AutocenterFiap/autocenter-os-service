package br.com.autocenterfiap.osservice.application.port;

import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import java.util.List;

public interface HistoricoRepositoryPort {

    void registrar(RegistroHistorico registro);

    List<RegistroHistorico> listar(Long osId);
}
