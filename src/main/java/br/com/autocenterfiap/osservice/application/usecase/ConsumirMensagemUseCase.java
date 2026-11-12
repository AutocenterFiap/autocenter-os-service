package br.com.autocenterfiap.osservice.application.usecase;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import br.com.autocenterfiap.osservice.application.port.MapeadorGatilhoPort;
import br.com.autocenterfiap.osservice.application.port.MensagemProcessadaPort;
import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ConsumirMensagemUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConsumirMensagemUseCase.class);

    private final MapeadorGatilhoPort mapeador;
    private final OrdemServicoRepositoryPort ordens;
    private final MensagemProcessadaPort processadas;
    private final ProcessarGatilhoSagaUseCase processar;
    private final ObservabilidadePort observabilidade;

    public ConsumirMensagemUseCase(MapeadorGatilhoPort mapeador, OrdemServicoRepositoryPort ordens,
            MensagemProcessadaPort processadas, ProcessarGatilhoSagaUseCase processar,
            ObservabilidadePort observabilidade) {
        this.mapeador = mapeador;
        this.ordens = ordens;
        this.processadas = processadas;
        this.processar = processar;
        this.observabilidade = observabilidade;
    }

    @Transactional
    public void executar(MensagemEnvelope mensagem) {
        validar(mensagem);
        var entrada = mapeador.mapear(mensagem);
        if (entrada.isEmpty()) {
            descartar(mensagem, "tipo desconhecido");
            return;
        }
        if (ordens.buscarPorId(mensagem.osId()).isEmpty()) {
            descartar(mensagem, "OS inexistente");
            return;
        }
        if (!processadas.registrarSeNova(mensagem.eventId())) {
            log.info("Mensagem {} repetida, ignorada", mensagem.eventId());
            return;
        }
        var saga = entrada.get();
        processar.executar(mensagem.osId(), saga.gatilho(), mensagem.tipo(), saga.itens(), saga.dados());
    }

    private static void validar(MensagemEnvelope mensagem) {
        if (mensagem.eventId() == null || mensagem.tipo() == null || mensagem.osId() == null) {
            throw new MensagemInvalidaException("Envelope sem eventId, tipo ou osId: eventId=" + mensagem.eventId()
                    + ", tipo=" + mensagem.tipo() + ", osId=" + mensagem.osId());
        }
    }

    private void descartar(MensagemEnvelope mensagem, String razao) {
        log.warn("Mensagem {} do tipo {} descartada, OS {}: {}", mensagem.eventId(), mensagem.tipo(),
                mensagem.osId(), razao);
        observabilidade.eventoDescartado(mensagem.tipo());
    }
}
