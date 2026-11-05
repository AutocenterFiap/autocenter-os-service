package br.com.autocenterfiap.osservice.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSaida;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.OutboxJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxAdapter implements OutboxPort {

    private final OutboxJpaRepository repositorio;
    private final ObjectMapper mapper;

    public OutboxAdapter(OutboxJpaRepository repositorio, ObjectMapper mapper) {
        this.repositorio = repositorio;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void guardar(MensagemSaida mensagem) {
        var envelope = mensagem.envelope();
        var e = new OutboxJpaEntity();
        e.setEventId(envelope.eventId().toString());
        e.setTopico(mensagem.topico());
        e.setChave(String.valueOf(envelope.osId()));
        try {
            e.setConteudo(mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nao foi possivel serializar o envelope " + envelope.eventId(), ex);
        }
        e.setCriadoEm(Instant.now());
        repositorio.save(e);
    }
}
