package br.com.autocenterfiap.osservice.infrastructure.persistence.repository;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.SagaJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SagaJpaRepository extends JpaRepository<SagaJpaEntity, Long> {

    @Query("""
            select s.osId from SagaJpaEntity s
            where s.prazoPagamento <= :agora
              and exists (select 1 from OrdemServicoJpaEntity o where o.id = s.osId and o.status in :status)
            order by s.prazoPagamento asc
            """)
    List<Long> osComPrazoVencido(@Param("agora") Instant agora, @Param("status") Collection<StatusOS> status,
            Pageable limite);
}
