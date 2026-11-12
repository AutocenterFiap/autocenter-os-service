package br.com.autocenterfiap.osservice.infrastructure.persistence.adapter;

import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.HistoricoJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.HistoricoJpaRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricoRepositoryAdapter implements HistoricoRepositoryPort {

    private final HistoricoJpaRepository repositorio;

    public HistoricoRepositoryAdapter(HistoricoJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public void registrar(RegistroHistorico r) {
        var e = new HistoricoJpaEntity();
        e.setOsId(r.osId());
        e.setStatusAnterior(r.statusAnterior());
        e.setStatusNovo(r.statusNovo());
        e.setEvento(r.evento());
        e.setMotivo(r.motivo());
        e.setData(r.data());
        repositorio.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistroHistorico> listar(Long osId) {
        return repositorio.findByOsIdOrderByIdAsc(osId).stream()
                .map(e -> new RegistroHistorico(e.getOsId(), e.getStatusAnterior(), e.getStatusNovo(),
                        e.getEvento(), e.getMotivo(), e.getData()))
                .toList();
    }
}
