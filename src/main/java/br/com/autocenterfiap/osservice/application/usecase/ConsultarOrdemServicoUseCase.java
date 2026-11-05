package br.com.autocenterfiap.osservice.application.usecase;

import br.com.autocenterfiap.osservice.application.exception.OrdemServicoNaoEncontradaException;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.Pagina;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ConsultarOrdemServicoUseCase {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    public record Detalhe(OrdemServico os, String linkPagamento) {
    }

    private final OrdemServicoRepositoryPort ordens;
    private final SagaRepositoryPort sagas;
    private final HistoricoRepositoryPort historico;

    public ConsultarOrdemServicoUseCase(OrdemServicoRepositoryPort ordens, SagaRepositoryPort sagas,
            HistoricoRepositoryPort historico) {
        this.ordens = ordens;
        this.sagas = sagas;
        this.historico = historico;
    }

    @Transactional(readOnly = true)
    public Pagina<OrdemServico> listar(StatusOS statusOuNull, int pagina, int tamanho) {
        var tamanhoEfetivo = tamanho < 1 ? TAMANHO_PADRAO : Math.min(tamanho, TAMANHO_MAXIMO);
        return ordens.listar(statusOuNull, Math.max(pagina, 0), tamanhoEfetivo);
    }

    @Transactional(readOnly = true)
    public Detalhe detalhar(Long id) {
        var os = buscar(id);
        var emPagamento = os.getStatus() == StatusOS.AGUARDANDO_PAGAMENTO
                || os.getStatus() == StatusOS.PAGAMENTO_RECUSADO;
        return new Detalhe(os, emPagamento ? sagas.buscar(id).getLinkPagamento() : null);
    }

    @Transactional(readOnly = true)
    public OrdemServico buscar(Long id) {
        return ordens.buscarPorId(id).orElseThrow(() -> new OrdemServicoNaoEncontradaException(id));
    }

    @Transactional(readOnly = true)
    public Detalhe detalharPorNumero(Long numero) {
        var os = ordens.buscarPorNumero(numero)
                .orElseThrow(() -> new OrdemServicoNaoEncontradaException(numero));
        return detalhar(os.getId());
    }

    @Transactional(readOnly = true)
    public List<RegistroHistorico> historico(Long id) {
        buscar(id);
        return historico.listar(id);
    }
}
