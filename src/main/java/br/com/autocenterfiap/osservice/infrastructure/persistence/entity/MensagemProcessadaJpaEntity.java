package br.com.autocenterfiap.osservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mensagem_processada")
@Getter
@Setter
public class MensagemProcessadaJpaEntity {

    @Id
    @Column(name = "event_id", length = 36, columnDefinition = "char(36)")
    private String eventId;

    @Column(name = "processada_em", nullable = false)
    private Instant processadaEm;
}
