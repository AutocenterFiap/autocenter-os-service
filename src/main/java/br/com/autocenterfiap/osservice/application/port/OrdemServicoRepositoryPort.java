package br.com.autocenterfiap.osservice.application.port;

import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.util.Optional;

public interface OrdemServicoRepositoryPort {

    OrdemServico salvar(OrdemServico os);

    Optional<OrdemServico> buscarPorId(Long id);

    Optional<OrdemServico> buscarPorNumero(Long numero);

    Pagina<OrdemServico> listar(StatusOS statusOuNull, int pagina, int tamanho);
}
