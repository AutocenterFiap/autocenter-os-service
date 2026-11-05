package br.com.autocenterfiap.osservice.application.port;

import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import java.time.Instant;
import java.util.List;

public interface SagaRepositoryPort {

    SagaOS salvar(SagaOS saga);

    SagaOS buscar(Long osId);

    List<Long> osComPrazoVencido(Instant agora, int limite);
}
