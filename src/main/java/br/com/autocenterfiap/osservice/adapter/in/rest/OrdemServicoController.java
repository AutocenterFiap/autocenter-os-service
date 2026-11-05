package br.com.autocenterfiap.osservice.adapter.in.rest;

import br.com.autocenterfiap.osservice.adapter.in.rest.dto.AbrirOSRequest;
import br.com.autocenterfiap.osservice.adapter.in.rest.dto.HistoricoResponse;
import br.com.autocenterfiap.osservice.adapter.in.rest.dto.OSResponse;
import br.com.autocenterfiap.osservice.adapter.in.rest.dto.PaginaResponse;
import br.com.autocenterfiap.osservice.adapter.in.rest.dto.StatusResponse;
import br.com.autocenterfiap.osservice.application.usecase.AbrirOrdemServicoUseCase;
import br.com.autocenterfiap.osservice.application.usecase.ConsultarOrdemServicoUseCase;
import br.com.autocenterfiap.osservice.application.usecase.ProcessarGatilhoSagaUseCase;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import br.com.autocenterfiap.osservice.application.exception.OrdemServicoNaoEncontradaException;
import br.com.autocenterfiap.osservice.infrastructure.security.UsuarioToken;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ordem-servicos")
public class OrdemServicoController {

    private final AbrirOrdemServicoUseCase abrir;
    private final ConsultarOrdemServicoUseCase consultar;
    private final ProcessarGatilhoSagaUseCase processar;

    public OrdemServicoController(AbrirOrdemServicoUseCase abrir, ConsultarOrdemServicoUseCase consultar,
            ProcessarGatilhoSagaUseCase processar) {
        this.abrir = abrir;
        this.consultar = consultar;
        this.processar = processar;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OSResponse abrir(@Valid @RequestBody AbrirOSRequest req) {
        var os = abrir.executar(req.clienteId(), req.veiculoId(), req.problemaRelatado());
        return OSResponse.de(os, null);
    }

    @GetMapping
    public PaginaResponse<OSResponse> listar(@RequestParam(required = false) StatusOS status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + ConsultarOrdemServicoUseCase.TAMANHO_PADRAO) int tamanho) {
        return PaginaResponse.de(consultar.listar(status, pagina, tamanho), os -> OSResponse.de(os, null));
    }

    @GetMapping("/{id}")
    public OSResponse detalhar(@PathVariable Long id) {
        var d = consultar.detalhar(id);
        return OSResponse.de(d.os(), d.linkPagamento());
    }

    @GetMapping("/numero/{numero}")
    public OSResponse porNumero(@PathVariable Long numero) {
        var d = consultar.detalharPorNumero(numero);
        return OSResponse.de(d.os(), d.linkPagamento());
    }

    @GetMapping("/status/{id}")
    public StatusResponse status(@PathVariable Long id, @AuthenticationPrincipal UsuarioToken usuario) {
        if (!usuario.cliente()) {
            return StatusResponse.de(consultar.buscar(id));
        }
        try {
            var os = consultar.buscar(id);
            if (usuario.clienteId() == null || !usuario.clienteId().equals(os.getClienteId())) {
                throw new AccessDeniedException("OS de outro cliente");
            }
            return StatusResponse.de(os);
        } catch (OrdemServicoNaoEncontradaException e) {
            throw new AccessDeniedException("OS inexistente");
        }
    }

    @GetMapping("/{id}/historico")
    public List<HistoricoResponse> historico(@PathVariable Long id) {
        return consultar.historico(id).stream().map(HistoricoResponse::de).toList();
    }

    @PostMapping("/{id}/entregar")
    public StatusResponse entregar(@PathVariable Long id) {
        acionar(id, TipoGatilho.ENTREGA_SOLICITADA, "os.entregue",
                "So e possivel entregar uma OS concluida");
        return StatusResponse.de(consultar.buscar(id));
    }

    @PostMapping("/{id}/cancelar")
    public StatusResponse cancelar(@PathVariable Long id) {
        acionar(id, TipoGatilho.CANCELAMENTO_SOLICITADO, "os.cancelada",
                "So e possivel cancelar uma OS antes do inicio da execucao");
        return StatusResponse.de(consultar.buscar(id));
    }

    @PostMapping("/{id}/reprocessar-estorno")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> reprocessarEstorno(@PathVariable Long id) {
        acionar(id, TipoGatilho.REPROCESSAR_ESTORNO, "estorno.reprocessado",
                "A OS nao tem estorno pendente para reprocessar");
        return Map.of("mensagem", "Estorno reenviado");
    }

    private void acionar(Long id, TipoGatilho tipo, String evento, String mensagemRecusa) {
        if (!processar.executar(id, Gatilho.de(tipo), evento, null, Map.of()).aceita()) {
            throw new AcaoRecusadaException(mensagemRecusa);
        }
    }
}
