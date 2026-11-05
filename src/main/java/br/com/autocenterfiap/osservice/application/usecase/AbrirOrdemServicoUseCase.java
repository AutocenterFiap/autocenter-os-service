package br.com.autocenterfiap.osservice.application.usecase;

import br.com.autocenterfiap.osservice.application.exception.CadastroNaoEncontradoException;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemSagaFactory;
import br.com.autocenterfiap.osservice.application.mensagem.TiposMensagem;
import br.com.autocenterfiap.osservice.application.port.CadastroPort;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OutboxPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class AbrirOrdemServicoUseCase {

    private final CadastroPort cadastro;
    private final OrdemServicoRepositoryPort ordens;
    private final SagaRepositoryPort sagas;
    private final HistoricoRepositoryPort historico;
    private final OutboxPort outbox;
    private final MensagemSagaFactory fabrica;
    private final Clock clock;

    public AbrirOrdemServicoUseCase(CadastroPort cadastro, OrdemServicoRepositoryPort ordens,
            SagaRepositoryPort sagas, HistoricoRepositoryPort historico, OutboxPort outbox,
            MensagemSagaFactory fabrica, Clock clock) {
        this.cadastro = cadastro;
        this.ordens = ordens;
        this.sagas = sagas;
        this.historico = historico;
        this.outbox = outbox;
        this.fabrica = fabrica;
        this.clock = clock;
    }

    @Transactional
    public OrdemServico executar(Long clienteId, Long veiculoId, String problemaRelatado) {
        if (!cadastro.clienteExiste(clienteId)) {
            throw new CadastroNaoEncontradoException("Cliente", clienteId);
        }
        if (!cadastro.veiculoExiste(veiculoId)) {
            throw new CadastroNaoEncontradoException("Veiculo", veiculoId);
        }
        var agora = clock.instant();
        var nova = OrdemServico.abrir(clienteId, veiculoId, problemaRelatado, UUID.randomUUID(), agora);
        var os = ordens.salvar(nova);
        sagas.salvar(new SagaOS(os.getId()));
        historico.registrar(new RegistroHistorico(os.getId(), null, os.getStatus(), TiposMensagem.OS_CRIADA,
                null, agora));
        outbox.guardar(fabrica.osCriada(os));
        return os;
    }
}
