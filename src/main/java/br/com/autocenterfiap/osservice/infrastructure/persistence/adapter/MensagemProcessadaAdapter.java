package br.com.autocenterfiap.osservice.infrastructure.persistence.adapter;

import br.com.autocenterfiap.osservice.application.port.MensagemProcessadaPort;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.MensagemProcessadaJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MensagemProcessadaAdapter implements MensagemProcessadaPort {

    private final MensagemProcessadaJpaRepository repositorio;

    public MensagemProcessadaAdapter(MensagemProcessadaJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public boolean registrarSeNova(UUID eventId) {
        return repositorio.inserirSeNova(eventId.toString(), Instant.now()) == 1;
    }
}
