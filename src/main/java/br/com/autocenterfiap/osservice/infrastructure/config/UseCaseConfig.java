package br.com.autocenterfiap.osservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSagaFactory;
import br.com.autocenterfiap.osservice.application.port.CadastroPort;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.MapeadorGatilhoPort;
import br.com.autocenterfiap.osservice.application.port.MensagemProcessadaPort;
import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.application.usecase.AbrirOrdemServicoUseCase;
import br.com.autocenterfiap.osservice.application.usecase.ConsumirMensagemUseCase;
import br.com.autocenterfiap.osservice.application.usecase.ProcessarGatilhoSagaUseCase;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    MensagemSagaFactory mensagemSagaFactory(ObjectMapper mapper, Clock clock,
            @Value("${saga.pagamento.validade-minutos}") int validadeMinutos) {
        return new MensagemSagaFactory(mapper, clock, validadeMinutos);
    }

    @Bean
    AbrirOrdemServicoUseCase abrirOrdemServicoUseCase(CadastroPort cadastro, OrdemServicoRepositoryPort ordens,
            SagaRepositoryPort sagas, HistoricoRepositoryPort historico, OutboxPort outbox,
            MensagemSagaFactory fabrica, ObservabilidadePort observabilidade, Clock clock) {
        return new AbrirOrdemServicoUseCase(cadastro, ordens, sagas, historico, outbox, fabrica, observabilidade,
                clock);
    }

    @Bean
    ProcessarGatilhoSagaUseCase processarGatilhoSagaUseCase(OrdemServicoRepositoryPort ordens,
            SagaRepositoryPort sagas, HistoricoRepositoryPort historico, OutboxPort outbox,
            MensagemSagaFactory fabrica, ObservabilidadePort observabilidade, Clock clock,
            @Value("${saga.pagamento.max-tentativas}") int maxTentativas,
            @Value("${saga.pagamento.validade-minutos}") int validadeMinutos) {
        return new ProcessarGatilhoSagaUseCase(ordens, sagas, historico, outbox, fabrica, observabilidade, clock,
                maxTentativas, validadeMinutos);
    }

    @Bean
    ConsumirMensagemUseCase consumirMensagemUseCase(MapeadorGatilhoPort mapeador, OrdemServicoRepositoryPort ordens,
            MensagemProcessadaPort processadas, ProcessarGatilhoSagaUseCase processar,
            ObservabilidadePort observabilidade) {
        return new ConsumirMensagemUseCase(mapeador, ordens, processadas, processar, observabilidade);
    }
}
