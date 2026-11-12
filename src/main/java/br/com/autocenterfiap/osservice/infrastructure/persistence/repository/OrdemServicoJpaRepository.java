package br.com.autocenterfiap.osservice.infrastructure.persistence.repository;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.OrdemServicoJpaEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdemServicoJpaRepository extends JpaRepository<OrdemServicoJpaEntity, Long> {

    Optional<OrdemServicoJpaEntity> findByNumero(Long numero);

    Page<OrdemServicoJpaEntity> findByStatus(StatusOS status, Pageable pageable);
}
