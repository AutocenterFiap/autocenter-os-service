package br.com.autocenterfiap.osservice.domain.saga;

import java.time.Instant;

public class SagaOS {

    private final Long osId;
    private int tentativasPagamento;
    private Instant prazoPagamento;
    private String linkPagamento;
    private Long orcamentoId;
    private Long pagamentoId;
    private Long pagamentoConfirmadoId;
    private Long estornoPendenteId;

    public SagaOS(Long osId) {
        this.osId = osId;
    }

    public Long getOsId() { return osId; }
    public int getTentativasPagamento() { return tentativasPagamento; }
    public void setTentativasPagamento(int tentativasPagamento) { this.tentativasPagamento = tentativasPagamento; }
    public Instant getPrazoPagamento() { return prazoPagamento; }
    public void setPrazoPagamento(Instant prazoPagamento) { this.prazoPagamento = prazoPagamento; }
    public String getLinkPagamento() { return linkPagamento; }
    public void setLinkPagamento(String linkPagamento) { this.linkPagamento = linkPagamento; }
    public Long getOrcamentoId() { return orcamentoId; }
    public void setOrcamentoId(Long orcamentoId) { this.orcamentoId = orcamentoId; }
    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }
    public Long getPagamentoConfirmadoId() { return pagamentoConfirmadoId; }
    public void setPagamentoConfirmadoId(Long pagamentoConfirmadoId) { this.pagamentoConfirmadoId = pagamentoConfirmadoId; }
    public Long getEstornoPendenteId() { return estornoPendenteId; }
    public void setEstornoPendenteId(Long estornoPendenteId) { this.estornoPendenteId = estornoPendenteId; }
}
