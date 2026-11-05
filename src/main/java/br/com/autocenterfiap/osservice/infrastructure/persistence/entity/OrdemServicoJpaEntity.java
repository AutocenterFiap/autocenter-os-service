package br.com.autocenterfiap.osservice.infrastructure.persistence.entity;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ordem_servico")
@Getter
@Setter
public class OrdemServicoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long numero;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    @Column(name = "problema_relatado", nullable = false, length = 1000)
    private String problemaRelatado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusOS status;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "correlation_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String correlationId;

    @Column(name = "veiculo_ativo_id")
    private Long veiculoAtivoId;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    @Version
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "os_id", nullable = false)
    private List<ItemServicoJpaEntity> servicos = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "os_id", nullable = false)
    private List<ItemProdutoJpaEntity> produtos = new ArrayList<>();
}
