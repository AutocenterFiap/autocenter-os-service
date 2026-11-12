package br.com.autocenterfiap.osservice.infrastructure.config;

import br.com.autocenterfiap.osservice.application.usecase.ExpirarPagamentosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgendadorPrazo {

    private static final Logger log = LoggerFactory.getLogger(AgendadorPrazo.class);

    private final ExpirarPagamentosUseCase expirar;

    public AgendadorPrazo(ExpirarPagamentosUseCase expirar) {
        this.expirar = expirar;
    }

    @Scheduled(fixedDelay = 30000)
    public void expirarPagamentos() {
        try {
            var canceladas = expirar.executar();
            if (canceladas > 0) {
                log.info("{} OS canceladas por prazo de pagamento vencido", canceladas);
            }
        } catch (RuntimeException e) {
            log.error("Falha na rodada de expiracao de pagamentos", e);
        }
    }
}
