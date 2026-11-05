package br.com.autocenterfiap.osservice.infrastructure.persistence.repository;

import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.MensagemProcessadaJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MensagemProcessadaJpaRepository extends JpaRepository<MensagemProcessadaJpaEntity, String> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO mensagem_processada (event_id, processada_em) VALUES (:eventId, :quando)",
            nativeQuery = true)
    int inserirSeNova(@Param("eventId") String eventId, @Param("quando") Instant quando);
}
