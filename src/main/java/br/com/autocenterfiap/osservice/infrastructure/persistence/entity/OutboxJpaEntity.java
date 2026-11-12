package br.com.autocenterfiap.osservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox")
@Getter
@Setter
public class OutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String eventId;

    @Column(nullable = false, length = 100)
    private String topico;

    @Column(nullable = false, length = 100)
    private String chave;

    @Column(nullable = false, columnDefinition = "mediumtext")
    private String conteudo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "enviado_em")
    private Instant enviadoEm;
}
