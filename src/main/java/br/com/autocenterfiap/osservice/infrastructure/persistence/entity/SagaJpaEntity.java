package br.com.autocenterfiap.osservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "saga_os")
@Getter
@Setter
public class SagaJpaEntity {

    @Id
    @Column(name = "os_id")
    private Long osId;

    @Column(name = "tentativas_pagamento", nullable = false)
    private int tentativasPagamento;

    @Column(name = "prazo_pagamento")
    private Instant prazoPagamento;

    @Column(name = "link_pagamento", length = 1000)
    private String linkPagamento;

    @Column(name = "orcamento_id")
    private Long orcamentoId;

    @Column(name = "pagamento_id")
    private Long pagamentoId;

    @Column(name = "pagamento_confirmado_id")
    private Long pagamentoConfirmadoId;

    @Column(name = "estorno_pendente_id")
    private Long estornoPendenteId;
}
