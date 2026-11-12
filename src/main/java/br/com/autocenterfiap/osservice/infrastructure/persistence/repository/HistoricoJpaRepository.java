package br.com.autocenterfiap.osservice.infrastructure.persistence.repository;

import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.HistoricoJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoJpaRepository extends JpaRepository<HistoricoJpaEntity, Long> {

    List<HistoricoJpaEntity> findByOsIdOrderByIdAsc(Long osId);
}
