package br.com.autocenterfiap.osservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.saga.Decisao;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

class ExpirarPagamentosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T12:00:00Z");

    private final SagaRepositoryPort sagas = mock(SagaRepositoryPort.class);
    private final ProcessarGatilhoSagaUseCase processar = mock(ProcessarGatilhoSagaUseCase.class);
    private final ExpirarPagamentosUseCase useCase =
            new ExpirarPagamentosUseCase(sagas, processar, Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    void prazoVencidoDisparaGatilhoComMotivoLinkExpirado() {
        when(sagas.osComPrazoVencido(AGORA, 100)).thenReturn(List.of(7L));
        when(processar.executar(eq(7L), any(), any(), isNull(), any())).thenReturn(aceita());

        var canceladas = useCase.executar();

        var gatilho = ArgumentCaptor.forClass(Gatilho.class);
        verify(processar).executar(eq(7L), gatilho.capture(), eq("prazo.vencido"), isNull(), eq(Map.of()));
        assertThat(gatilho.getValue().tipo()).isEqualTo(TipoGatilho.PRAZO_VENCIDO);
        assertThat(canceladas).isEqualTo(1);
    }

    @Test
    void falhaEmUmaOSNaoImpedeAsDemais() {
        when(sagas.osComPrazoVencido(AGORA, 100)).thenReturn(List.of(1L, 2L, 3L));
        when(processar.executar(eq(1L), any(), any(), any(), any())).thenReturn(aceita());
        when(processar.executar(eq(2L), any(), any(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("conflito"));
        when(processar.executar(eq(3L), any(), any(), any(), any())).thenReturn(Decisao.ignorar());

        var canceladas = useCase.executar();

        verify(processar).executar(eq(3L), any(), any(), any(), any());
        assertThat(canceladas).isEqualTo(1);
    }

    private static Decisao aceita() {
        return new Decisao(true, List.of(), List.of(), null, null, false, false);
    }
}
