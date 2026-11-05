package br.com.autocenterfiap.osservice.application.usecase;

import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExpirarPagamentosUseCase {

    static final int LIMITE_POR_RODADA = 100;
    static final String EVENTO = "prazo.vencido";

    private static final Logger log = LoggerFactory.getLogger(ExpirarPagamentosUseCase.class);

    private final SagaRepositoryPort sagas;
    private final ProcessarGatilhoSagaUseCase processar;
    private final Clock clock;

    public ExpirarPagamentosUseCase(SagaRepositoryPort sagas, ProcessarGatilhoSagaUseCase processar, Clock clock) {
        this.sagas = sagas;
        this.processar = processar;
        this.clock = clock;
    }

    public int executar() {
        int canceladas = 0;
        for (var osId : sagas.osComPrazoVencido(clock.instant(), LIMITE_POR_RODADA)) {
            try {
                var decisao = processar.executar(osId, Gatilho.de(TipoGatilho.PRAZO_VENCIDO), EVENTO, null, Map.of());
                if (decisao.aceita()) {
                    canceladas++;
                }
            } catch (RuntimeException e) {
                log.error("Falha ao expirar o pagamento da OS {}; segue para as demais", osId, e);
            }
        }
        return canceladas;
    }
}
