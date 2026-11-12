package br.com.autocenterfiap.osservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.usecase.ConsumirMensagemUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ConsumidorSagaTest {

    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final String MENSAGEM = """
            {"eventId":"%s","tipo":"execucao.finalizada","versao":1,"osId":10,"correlationId":"%s",
             "ocorridoEm":"2026-11-05T14:30:00Z","payload":{},"campoNovo":true}"""
            .formatted(UUID.randomUUID(), CORRELATION_ID);

    private final ConsumirMensagemUseCase useCase = mock(ConsumirMensagemUseCase.class);
    private final ConsumidorSaga consumidor =
            new ConsumidorSaga(new ObjectMapper().registerModule(new JavaTimeModule()), useCase);

    @Test
    void colocaCorrelationIdNoMdcDuranteOProcessamentoERemoveDepois() throws Exception {
        var vistoNoMdc = new AtomicReference<String>();
        doAnswer(i -> {
            vistoNoMdc.set(MDC.get("correlationId"));
            return null;
        }).when(useCase).executar(any());

        consumidor.consumir(MENSAGEM);

        assertThat(vistoNoMdc.get()).isEqualTo(CORRELATION_ID);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void removeCorrelationIdDoMdcMesmoComErro() {
        doThrow(new IllegalStateException("erro")).when(useCase).executar(any());

        assertThatThrownBy(() -> consumidor.consumir(MENSAGEM)).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void jsonInvalidoPropagaJsonProcessingException() {
        assertThatThrownBy(() -> consumidor.consumir("isto nao e json"))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    void jsonNuloEInvalido() {
        assertThatThrownBy(() -> consumidor.consumir("null")).isInstanceOf(MensagemInvalidaException.class);
        verifyNoInteractions(useCase);
    }

    @Test
    void envelopeSemCorrelationIdNaoPoeTextoNuloNoMdc() throws Exception {
        var vistoNoMdc = new AtomicReference<String>("nao chamado");
        doAnswer(i -> {
            vistoNoMdc.set(MDC.get("correlationId"));
            return null;
        }).when(useCase).executar(any());

        consumidor.consumir("{\"eventId\":\"" + UUID.randomUUID() + "\",\"tipo\":\"x\",\"osId\":10}");

        assertThat(vistoNoMdc.get()).isEmpty();
    }
}
