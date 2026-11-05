package br.com.autocenterfiap.osservice.infrastructure.persistence.adapter;

import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.SagaJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.SagaJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SagaRepositoryAdapter implements SagaRepositoryPort {

    private static final List<StatusOS> STATUS_DE_PAGAMENTO =
            List.of(StatusOS.AGUARDANDO_PAGAMENTO, StatusOS.PAGAMENTO_RECUSADO);

    private final SagaJpaRepository repositorio;

    public SagaRepositoryAdapter(SagaJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public SagaOS salvar(SagaOS saga) {
        var e = new SagaJpaEntity();
        e.setOsId(saga.getOsId());
        e.setTentativasPagamento(saga.getTentativasPagamento());
        e.setPrazoPagamento(saga.getPrazoPagamento());
        e.setLinkPagamento(saga.getLinkPagamento());
        e.setOrcamentoId(saga.getOrcamentoId());
        e.setPagamentoId(saga.getPagamentoId());
        e.setPagamentoConfirmadoId(saga.getPagamentoConfirmadoId());
        e.setEstornoPendenteId(saga.getEstornoPendenteId());
        return paraDominio(repositorio.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public SagaOS buscar(Long osId) {
        return repositorio.findById(osId).map(this::paraDominio).orElseGet(() -> new SagaOS(osId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> osComPrazoVencido(Instant agora, int limite) {
        return repositorio.osComPrazoVencido(agora, STATUS_DE_PAGAMENTO, PageRequest.of(0, limite));
    }

    private SagaOS paraDominio(SagaJpaEntity e) {
        var s = new SagaOS(e.getOsId());
        s.setTentativasPagamento(e.getTentativasPagamento());
        s.setPrazoPagamento(e.getPrazoPagamento());
        s.setLinkPagamento(e.getLinkPagamento());
        s.setOrcamentoId(e.getOrcamentoId());
        s.setPagamentoId(e.getPagamentoId());
        s.setPagamentoConfirmadoId(e.getPagamentoConfirmadoId());
        s.setEstornoPendenteId(e.getEstornoPendenteId());
        return s;
    }
}
