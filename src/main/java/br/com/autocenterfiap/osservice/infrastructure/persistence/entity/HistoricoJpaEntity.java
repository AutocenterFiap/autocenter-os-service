package br.com.autocenterfiap.osservice.infrastructure.persistence.entity;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "os_historico")
@Getter
@Setter
public class HistoricoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "os_id", nullable = false)
    private Long osId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 30)
    private StatusOS statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 30)
    private StatusOS statusNovo;

    @Column(nullable = false, length = 100)
    private String evento;

    @Column(length = 500)
    private String motivo;

    @Column(nullable = false)
    private Instant data;
}
