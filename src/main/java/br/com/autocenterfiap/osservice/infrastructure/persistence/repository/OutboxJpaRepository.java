package br.com.autocenterfiap.osservice.infrastructure.persistence.repository;

import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.OutboxJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    List<OutboxJpaEntity> findTop100ByEnviadoEmIsNullOrderByIdAsc();

    @Transactional
    @Modifying
    @Query("update OutboxJpaEntity o set o.enviadoEm = :quando where o.id = :id")
    int marcarEnviada(@Param("id") Long id, @Param("quando") Instant quando);
}
