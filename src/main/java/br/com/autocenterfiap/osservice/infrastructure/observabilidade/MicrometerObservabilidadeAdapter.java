package br.com.autocenterfiap.osservice.infrastructure.observabilidade;

import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MicrometerObservabilidadeAdapter implements ObservabilidadePort {

    private final MeterRegistry registro;

    public MicrometerObservabilidadeAdapter(MeterRegistry registro) {
        this.registro = registro;
    }

    @Override
    public void osCriada() {
        registro.counter("autocenter.os.criada.total").increment();
    }

    @Override
    public void tempoNoStatus(StatusOS status, Duration tempo) {
        registro.timer("autocenter.os.tempo_status.seconds", "status", status.name()).record(tempo);
    }

    @Override
    public void mudancaStatus(StatusOS novoStatus) {
        registro.counter("autocenter.os.status.total", "status", novoStatus.name()).increment();
    }

    @Override
    public void sagaCompensada(String motivo) {
        registro.counter("autocenter.saga.compensada.total", "motivo", motivo).increment();
    }

    @Override
    public void eventoDescartado(String tipo) {
        registro.counter("autocenter.saga.evento_descartado.total", "tipo", tipo).increment();
    }

    @Override
    public void estornoFalhou() {
        registro.counter("autocenter.saga.estorno_falhou.total").increment();
    }

    @Override
    public void mensagemDlt() {
        registro.counter("autocenter.saga.mensagem_dlt.total").increment();
    }
}
