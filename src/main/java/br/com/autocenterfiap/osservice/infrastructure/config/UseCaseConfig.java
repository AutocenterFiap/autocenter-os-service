package br.com.autocenterfiap.osservice.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSagaFactory;
import br.com.autocenterfiap.osservice.application.port.CadastroPort;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.application.usecase.AbrirOrdemServicoUseCase;
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
            MensagemSagaFactory fabrica, Clock clock) {
        return new AbrirOrdemServicoUseCase(cadastro, ordens, sagas, historico, outbox, fabrica, clock);
    }
}
