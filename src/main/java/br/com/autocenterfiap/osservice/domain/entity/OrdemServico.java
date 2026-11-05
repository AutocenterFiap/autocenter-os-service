package br.com.autocenterfiap.osservice.domain.entity;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrdemServico {

    private Long id;
    private Long numero;
    private final Long clienteId;
    private final Long veiculoId;
    private final String problemaRelatado;
    private StatusOS status;
    private BigDecimal valorTotal;
    private ItensOS itens;
    private final UUID correlationId;
    private final Instant criadaEm;
    private Instant atualizadaEm;
    private final Long version;

    private OrdemServico(Long id, Long numero, Long clienteId, Long veiculoId, String problemaRelatado,
            StatusOS status, BigDecimal valorTotal, ItensOS itens, UUID correlationId,
            Instant criadaEm, Instant atualizadaEm, Long version) {
        this.id = id;
        this.numero = numero;
        this.clienteId = clienteId;
        this.veiculoId = veiculoId;
        this.problemaRelatado = problemaRelatado;
        this.status = status;
        this.valorTotal = valorTotal;
        this.itens = itens;
        this.correlationId = correlationId;
        this.criadaEm = criadaEm;
        this.atualizadaEm = atualizadaEm;
        this.version = version;
    }

    public static OrdemServico abrir(Long clienteId, Long veiculoId, String problema,
            UUID correlationId, Instant agora) {
        return new OrdemServico(null, null, clienteId, veiculoId, problema, StatusOS.ABERTA,
                BigDecimal.ZERO.setScale(2), ItensOS.VAZIO, correlationId, agora, agora, null);
    }

    public static OrdemServico reconstruir(Long id, Long numero, Long clienteId, Long veiculoId,
            String problemaRelatado, StatusOS status, BigDecimal valorTotal, ItensOS itens,
            UUID correlationId, Instant criadaEm, Instant atualizadaEm, Long version) {
        return new OrdemServico(id, numero, clienteId, veiculoId, problemaRelatado, status, valorTotal,
                itens, correlationId, criadaEm, atualizadaEm, version);
    }

    public void mudarStatus(StatusOS novo, Instant agora) {
        this.status = novo;
        this.atualizadaEm = agora;
    }

    public void registrarItens(ItensOS itens, Instant agora) {
        this.itens = itens;
        this.valorTotal = itens.total();
        this.atualizadaEm = agora;
    }

    public void definirIdENumero(Long id, Long numero) {
        this.id = id;
        this.numero = numero;
    }

    public Long getId() { return id; }
    public Long getNumero() { return numero; }
    public Long getClienteId() { return clienteId; }
    public Long getVeiculoId() { return veiculoId; }
    public String getProblemaRelatado() { return problemaRelatado; }
    public StatusOS getStatus() { return status; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public ItensOS getItens() { return itens; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public Long getVersion() { return version; }
}
