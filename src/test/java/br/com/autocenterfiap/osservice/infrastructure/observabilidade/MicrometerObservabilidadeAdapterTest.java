package br.com.autocenterfiap.osservice.infrastructure.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MicrometerObservabilidadeAdapterTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MicrometerObservabilidadeAdapter adapter = new MicrometerObservabilidadeAdapter(registro);

    @Test
    void contaEventosDaSaga() {
        adapter.osCriada();
        adapter.mudancaStatus(StatusOS.CANCELADA);
        adapter.sagaCompensada("LINK_EXPIRADO");
        adapter.eventoDescartado("evento.qualquer");
        adapter.estornoFalhou();

        assertThat(registro.get("autocenter.os.criada.total").counter().count()).isEqualTo(1);
        assertThat(registro.get("autocenter.os.status.total").tag("status", "CANCELADA").counter().count())
                .isEqualTo(1);
        assertThat(registro.get("autocenter.saga.compensada.total").tag("motivo", "LINK_EXPIRADO").counter().count())
                .isEqualTo(1);
        assertThat(registro.get("autocenter.saga.evento_descartado.total").tag("tipo", "evento.qualquer")
                .counter().count()).isEqualTo(1);
        assertThat(registro.get("autocenter.saga.estorno_falhou.total").counter().count()).isEqualTo(1);
    }

    @Test
    void registraTempoNoStatus() {
        adapter.tempoNoStatus(StatusOS.ABERTA, Duration.ofSeconds(90));

        assertThat(registro.get("autocenter.os.tempo_status.seconds").tag("status", "ABERTA").timer()
                .totalTime(TimeUnit.SECONDS)).isEqualTo(90.0);
    }
}
