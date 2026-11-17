package br.com.autocenterfiap.osservice.adapter.in.rest;

import br.com.autocenterfiap.osservice.adapter.in.rest.dto.AbrirOSRequest;
import br.com.autocenterfiap.osservice.adapter.in.rest.dto.ErroResponse;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Ordens de Serviço",
        description = "Abertura, consulta e ações do ciclo de vida da OS orquestrado pela Saga")
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

    @Operation(summary = "Abrir ordem de serviço",
            description = "Valida cliente e veículo no Cadastro, cria a OS com status ABERTA e publica o evento "
                    + "os.criada, que inicia a Saga")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "OS aberta"),
            @ApiResponse(responseCode = "400", description = "Campos obrigatórios ausentes ou inválidos",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Cliente ou veículo não encontrado ou inativo no Cadastro",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe uma OS em andamento para o veículo",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "503", description = "Serviço de Cadastro indisponível",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OSResponse abrir(@Valid @RequestBody AbrirOSRequest req) {
        var os = abrir.executar(req.clienteId(), req.veiculoId(), req.problemaRelatado());
        return OSResponse.de(os);
    }

    @Operation(summary = "Listar ordens de serviço",
            description = "Lista as OS de forma paginada, com filtro opcional por status. "
                    + "O tamanho máximo da página é 100")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página retornada"),
            @ApiResponse(responseCode = "400", description = "Status ou parâmetro de paginação inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @GetMapping
    public PaginaResponse<OSResponse> listar(
            @Parameter(description = "Filtra pelo status da OS") @RequestParam(required = false) StatusOS status,
            @Parameter(description = "Número da página, iniciando em 0", example = "0")
            @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Quantidade de itens por página (máximo 100)", example = "20")
            @RequestParam(defaultValue = "" + ConsultarOrdemServicoUseCase.TAMANHO_PADRAO) int tamanho) {
        return PaginaResponse.de(consultar.listar(status, pagina, tamanho), OSResponse::de);
    }

    @Operation(summary = "Detalhar ordem de serviço",
            description = "Retorna a OS com itens, valor total, link de pagamento e indicação de estorno pendente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OS encontrada"),
            @ApiResponse(responseCode = "400", description = "Identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @GetMapping("/{id}")
    public OSResponse detalhar(
            @Parameter(description = "Identificador interno da OS", example = "42") @PathVariable Long id) {
        var d = consultar.detalhar(id);
        return OSResponse.detalhe(d.os(), d.linkPagamento(), d.estornoPendente());
    }

    @Operation(summary = "Buscar ordem de serviço pelo número",
            description = "Mesmo retorno do detalhe, localizando a OS pelo número sequencial informado ao cliente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OS encontrada"),
            @ApiResponse(responseCode = "400", description = "Número em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @GetMapping("/numero/{numero}")
    public OSResponse porNumero(
            @Parameter(description = "Número sequencial da OS", example = "1001") @PathVariable Long numero) {
        var d = consultar.detalharPorNumero(numero);
        return OSResponse.detalhe(d.os(), d.linkPagamento(), d.estornoPendente());
    }

    @Operation(summary = "Consultar status da ordem de serviço",
            description = "Disponível para funcionários e clientes. Um token de cliente só consulta "
                    + "as próprias OS; OS de outro cliente ou inexistente retorna 403 para não revelar "
                    + "sua existência")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retornado"),
            @ApiResponse(responseCode = "400", description = "Identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403",
                    description = "OS de outro cliente ou inexistente, para token de cliente",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Ordem de serviço não encontrada, para token de funcionário",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @GetMapping("/status/{id}")
    public StatusResponse status(
            @Parameter(description = "Identificador interno da OS", example = "42") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioToken usuario) {
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

    @Operation(summary = "Histórico da ordem de serviço",
            description = "Lista as transições de status da OS em ordem cronológica, com o evento e "
                    + "o motivo de cada uma")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico retornado"),
            @ApiResponse(responseCode = "400", description = "Identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @GetMapping("/{id}/historico")
    public List<HistoricoResponse> historico(
            @Parameter(description = "Identificador interno da OS", example = "42") @PathVariable Long id) {
        return consultar.historico(id).stream().map(HistoricoResponse::de).toList();
    }

    @Operation(summary = "Entregar veículo",
            description = "Registra a retirada do veículo pelo cliente, levando a OS de CONCLUIDA para ENTREGUE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OS entregue"),
            @ApiResponse(responseCode = "400", description = "Identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "A OS não está CONCLUIDA ou foi alterada por outra operação",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @PostMapping("/{id}/entregar")
    public StatusResponse entregar(
            @Parameter(description = "Identificador interno da OS", example = "42") @PathVariable Long id) {
        acionar(id, TipoGatilho.ENTREGA_SOLICITADA, "os.entregue",
                "So e possivel entregar uma OS concluida");
        return StatusResponse.de(consultar.buscar(id));
    }

    @Operation(summary = "Cancelar ordem de serviço",
            description = "Cancela a OS enquanto o pagamento não foi confirmado, ou seja, de ABERTA até "
                    + "PAGAMENTO_RECUSADO")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OS cancelada"),
            @ApiResponse(responseCode = "400", description = "Identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Pagamento já confirmado, OS encerrada ou alterada por outra operação",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @PostMapping("/{id}/cancelar")
    public StatusResponse cancelar(
            @Parameter(description = "Identificador interno da OS", example = "42") @PathVariable Long id) {
        acionar(id, TipoGatilho.CANCELAMENTO_SOLICITADO, "os.cancelada",
                "So e possivel cancelar uma OS antes do inicio da execucao");
        return StatusResponse.de(consultar.buscar(id));
    }

    @Operation(summary = "Reprocessar estorno",
            description = "Reenvia ao Billing o comando de estorno de uma OS cujo estorno ficou pendente")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Comando de estorno reenviado"),
            @ApiResponse(responseCode = "400", description = "Identificador em formato inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "403", description = "Token sem o perfil interno (funcionário)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ordem de serviço não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "A OS não tem estorno pendente para reprocessar",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    @PostMapping("/{id}/reprocessar-estorno")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> reprocessarEstorno(
            @Parameter(description = "Identificador interno da OS", example = "42") @PathVariable Long id) {
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
