package br.com.autocenterfiap.osservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.autocenterfiap.osservice.application.exception.CadastroNaoEncontradoException;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSagaFactory;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSaida;
import br.com.autocenterfiap.osservice.application.mensagem.TiposMensagem;
import br.com.autocenterfiap.osservice.application.mensagem.Topicos;
import br.com.autocenterfiap.osservice.application.port.CadastroPort;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbrirOrdemServicoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T14:30:00Z");

    private final CadastroPort cadastro = mock(CadastroPort.class);
    private final OrdemServicoRepositoryPort osRepo = mock(OrdemServicoRepositoryPort.class);
    private final SagaRepositoryPort sagaRepo = mock(SagaRepositoryPort.class);
    private final HistoricoRepositoryPort historico = mock(HistoricoRepositoryPort.class);
    private final OutboxPort outbox = mock(OutboxPort.class);
    private AbrirOrdemServicoUseCase useCase;

    @BeforeEach
    void preparar() {
        var clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        var fabrica = new MensagemSagaFactory(new ObjectMapper().registerModule(new JavaTimeModule()), clock, 30);
        useCase = new AbrirOrdemServicoUseCase(cadastro, osRepo, sagaRepo, historico, outbox, fabrica, clock);
        when(cadastro.clienteExiste(1L)).thenReturn(true);
        when(cadastro.veiculoExiste(2L)).thenReturn(true);
        when(osRepo.salvar(any())).thenAnswer(i -> {
            OrdemServico os = i.getArgument(0);
            os.definirIdENumero(10L, 10L);
            return os;
        });
    }

    @Test
    void abreOSCriaSagaHistoricoEOutbox() {
        var os = useCase.executar(1L, 2L, "Barulho no motor");

        assertThat(os.getId()).isEqualTo(10L);
        assertThat(os.getStatus()).isEqualTo(StatusOS.ABERTA);
        assertThat(os.getCriadaEm()).isEqualTo(AGORA);
        assertThat(os.getCorrelationId()).isNotNull();

        var saga = ArgumentCaptor.forClass(SagaOS.class);
        verify(sagaRepo).salvar(saga.capture());
        assertThat(saga.getValue().getOsId()).isEqualTo(10L);

        var reg = ArgumentCaptor.forClass(RegistroHistorico.class);
        verify(historico).registrar(reg.capture());
        assertThat(reg.getValue().osId()).isEqualTo(10L);
        assertThat(reg.getValue().statusAnterior()).isNull();
        assertThat(reg.getValue().statusNovo()).isEqualTo(StatusOS.ABERTA);
        assertThat(reg.getValue().evento()).isEqualTo("os.criada");
        assertThat(reg.getValue().data()).isEqualTo(AGORA);

        var msg = ArgumentCaptor.forClass(MensagemSaida.class);
        verify(outbox).guardar(msg.capture());
        assertThat(msg.getValue().topico()).isEqualTo(Topicos.OS_EVENTOS);
        assertThat(msg.getValue().envelope().tipo()).isEqualTo(TiposMensagem.OS_CRIADA);
        assertThat(msg.getValue().envelope().correlationId()).isEqualTo(os.getCorrelationId());
    }

    @Test
    void clienteInexistenteNaoCriaNada() {
        when(cadastro.clienteExiste(1L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(1L, 2L, "x"))
                .isInstanceOf(CadastroNaoEncontradoException.class);

        verifyNoInteractions(osRepo, sagaRepo, historico, outbox);
    }

    @Test
    void veiculoInexistenteNaoCriaNada() {
        when(cadastro.veiculoExiste(2L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(1L, 2L, "x"))
                .isInstanceOf(CadastroNaoEncontradoException.class);

        verifyNoInteractions(osRepo, sagaRepo, historico, outbox);
    }
}
