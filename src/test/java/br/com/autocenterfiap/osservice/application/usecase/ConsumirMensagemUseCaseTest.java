package br.com.autocenterfiap.osservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.mensagem.EntradaSaga;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import br.com.autocenterfiap.osservice.application.port.MapeadorGatilhoPort;
import br.com.autocenterfiap.osservice.application.port.MensagemProcessadaPort;
import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsumirMensagemUseCaseTest {

    private static final Long OS_ID = 10L;
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final EntradaSaga ENTRADA = new EntradaSaga(Gatilho.de(TipoGatilho.DIAGNOSTICO_INICIADO),
            ItensOS.VAZIO, Map.of("orcamentoId", 55L));

    private final MapeadorGatilhoPort mapeador = mock(MapeadorGatilhoPort.class);
    private final OrdemServicoRepositoryPort ordens = mock(OrdemServicoRepositoryPort.class);
    private final MensagemProcessadaPort processadas = mock(MensagemProcessadaPort.class);
    private final ProcessarGatilhoSagaUseCase processar = mock(ProcessarGatilhoSagaUseCase.class);
    private final ObservabilidadePort observabilidade = mock(ObservabilidadePort.class);
    private final ConsumirMensagemUseCase useCase =
            new ConsumirMensagemUseCase(mapeador, ordens, processadas, processar, observabilidade);

    private final MensagemEnvelope envelope = envelope(EVENT_ID, "diagnostico.iniciado", OS_ID);

    private static MensagemEnvelope envelope(UUID eventId, String tipo, Long osId) {
        return new MensagemEnvelope(eventId, tipo, 1, osId, UUID.randomUUID(), Instant.now(),
                new ObjectMapper().createObjectNode());
    }

    @BeforeEach
    void preparar() {
        when(mapeador.mapear(any())).thenReturn(Optional.of(ENTRADA));
        when(ordens.buscarPorId(OS_ID)).thenReturn(Optional.of(mock(OrdemServico.class)));
        when(processadas.registrarSeNova(EVENT_ID)).thenReturn(true);
    }

    @Test
    void mensagemNovaRegistraEProcessa() {
        useCase.executar(envelope);

        var ordem = inOrder(processadas, processar);
        ordem.verify(processadas).registrarSeNova(EVENT_ID);
        ordem.verify(processar).executar(OS_ID, ENTRADA.gatilho(), "diagnostico.iniciado", ENTRADA.itens(),
                ENTRADA.dados());
        verifyNoInteractions(observabilidade);
    }

    @Test
    void mensagemRepetidaNaoProcessa() {
        when(processadas.registrarSeNova(EVENT_ID)).thenReturn(false);

        useCase.executar(envelope);

        verifyNoInteractions(processar);
    }

    @Test
    void tipoDesconhecidoEDescartadoEContado() {
        when(mapeador.mapear(any())).thenReturn(Optional.empty());

        useCase.executar(envelope);

        verify(observabilidade).eventoDescartado("diagnostico.iniciado");
        verifyNoInteractions(processar, processadas);
    }

    @Test
    void osInexistenteEDescartadaSemExcecaoESemChamarOProcessamento() {
        when(ordens.buscarPorId(OS_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.executar(envelope)).doesNotThrowAnyException();

        verify(observabilidade).eventoDescartado("diagnostico.iniciado");
        verifyNoInteractions(processar);
        verify(processadas, never()).registrarSeNova(any());
    }

    @Test
    void envelopeSemEventIdEInvalido() {
        assertThatThrownBy(() -> useCase.executar(envelope(null, "diagnostico.iniciado", OS_ID)))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("eventId");
        verifyNoInteractions(processar);
    }

    @Test
    void envelopeSemTipoEInvalido() {
        assertThatThrownBy(() -> useCase.executar(envelope(EVENT_ID, null, OS_ID)))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("tipo");
    }

    @Test
    void envelopeSemOsIdEInvalido() {
        assertThatThrownBy(() -> useCase.executar(envelope(EVENT_ID, "diagnostico.iniciado", null)))
                .isInstanceOf(MensagemInvalidaException.class).hasMessageContaining("osId");
    }
}
