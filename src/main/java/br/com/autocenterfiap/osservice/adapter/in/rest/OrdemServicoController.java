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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Ordens de Servico", description = "Abertura, consulta e acoes manuais da ordem de servico. As demais "
        + "mudancas de status chegam pela Saga (Kafka)")
@ApiResponse(responseCode = "401", description = "Token ausente, invalido ou expirado",
        content = @Content(schema = @Schema(implementation = ErroResponse.class),
                examples = @ExampleObject(value = OrdemServicoController.EX_401)))
@ApiResponse(responseCode = "403", description = "O perfil do token nao pode usar esta rota (rota so do perfil "
        + "interno)", content = @Content(schema = @Schema(implementation = ErroResponse.class),
                examples = @ExampleObject(value = OrdemServicoController.EX_403)))
public class OrdemServicoController {

    static final String EX_401 = "{\"erro\":\"nao_autenticado\",\"mensagem\":\"Token ausente, invalido ou expirado\"}";
    static final String EX_403 = "{\"erro\":\"acesso_negado\",\"mensagem\":\"Acesso negado a este recurso\"}";
    private static final String EX_404 = "{\"erro\":\"nao_encontrado\",\"mensagem\":\"Ordem de servico nao encontrada: 99\"}";
    private static final String EX_ID_INVALIDO =
            "{\"erro\":\"requisicao_invalida\",\"mensagem\":\"Valor invalido para o parametro 'id': abc\"}";
    private static final String EX_CONCORRENCIA = "{\"erro\":\"conflito_concorrencia\",\"mensagem\":"
            + "\"A ordem de servico foi alterada por outra operacao; tente novamente\"}";
    private static final String DESC_ID = "Id interno da OS (campo `id` da resposta da abertura ou da lista)";

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
    @Operation(summary = "Abrir ordem de servico",
            description = "Cria a OS no status ABERTA e inicia a Saga: grava a OS, o estado da Saga e o "
                    + "historico, e publica `os.criada` em `os.eventos` (via outbox). O cliente e o veiculo sao "
                    + "conferidos no Cadastro, e o veiculo nao pode ter outra OS em andamento (de ABERTA ate "
                    + "EM_EXECUCAO). Perfil: interno.")
    @ApiResponse(responseCode = "201", description = "OS aberta (status ABERTA, sem itens e valor 0.00)")
    @ApiResponse(responseCode = "400", description = "Corpo ausente, JSON invalido ou campo obrigatorio faltando "
            + "/ fora do limite", content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = "{\"erro\":\"requisicao_invalida\",\"mensagem\":"
                            + "\"clienteId e obrigatorio; problemaRelatado e obrigatorio\"}")))
    @ApiResponse(responseCode = "404", description = "Cliente ou veiculo nao existe no Cadastro",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = "{\"erro\":\"nao_encontrado\",\"mensagem\":"
                            + "\"Veiculo nao encontrado: 42\"}")))
    @ApiResponse(responseCode = "409", description = "O veiculo ja tem uma OS em andamento",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = "{\"erro\":\"os_em_andamento\",\"mensagem\":"
                            + "\"Ja existe uma OS em andamento para o veiculo 42\"}")))
    public OSResponse abrir(@Valid @RequestBody AbrirOSRequest req) {
        var os = abrir.executar(req.clienteId(), req.veiculoId(), req.problemaRelatado());
        return OSResponse.de(os);
    }

    @GetMapping
    @Operation(summary = "Listar ordens de servico",
            description = "Lista paginada das OSs, da mais recente para a mais antiga, com filtro opcional por "
                    + "status. Valores fora do limite sao ajustados: pagina negativa vira 0, tamanho menor que 1 "
                    + "vira 20 e maior que 100 vira 100. Nao traz `linkPagamento` nem `estornoPendente` (use o "
                    + "detalhe). Perfil: interno.")
    @ApiResponse(responseCode = "200", description = "Pagina de OSs (pode vir vazia)")
    @ApiResponse(responseCode = "400", description = "Status inexistente ou pagina/tamanho nao numericos",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = "{\"erro\":\"requisicao_invalida\",\"mensagem\":"
                            + "\"Valor invalido para o parametro 'status': FINALIZADA\"}")))
    public PaginaResponse<OSResponse> listar(
            @Parameter(description = "Filtra pelo status atual. Sem o parametro, lista todos",
                    example = "AGUARDANDO_PAGAMENTO")
            @RequestParam(required = false) StatusOS status,
            @Parameter(description = "Numero da pagina, comecando em 0", example = "0")
            @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Itens por pagina (1 a 100; padrao 20)", example = "20")
            @RequestParam(defaultValue = "" + ConsultarOrdemServicoUseCase.TAMANHO_PADRAO) int tamanho) {
        return PaginaResponse.de(consultar.listar(status, pagina, tamanho), OSResponse::de);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar ordem de servico pelo id",
            description = "OS completa: itens e valor do diagnostico, `linkPagamento` (so em AGUARDANDO_PAGAMENTO "
                    + "ou PAGAMENTO_RECUSADO) e `estornoPendente`. Perfil: interno.")
    @ApiResponse(responseCode = "200", description = "OS encontrada")
    @ApiResponse(responseCode = "400", description = "Id nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_ID_INVALIDO)))
    @ApiResponse(responseCode = "404", description = "OS nao existe",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    public OSResponse detalhar(@Parameter(description = DESC_ID, example = "15") @PathVariable Long id) {
        var d = consultar.detalhar(id);
        return OSResponse.detalhe(d.os(), d.linkPagamento(), d.estornoPendente());
    }

    @GetMapping("/numero/{numero}")
    @Operation(summary = "Detalhar ordem de servico pelo numero",
            description = "Mesmo retorno do detalhe por id, buscando pelo numero mostrado ao cliente. "
                    + "Perfil: interno.")
    @ApiResponse(responseCode = "200", description = "OS encontrada")
    @ApiResponse(responseCode = "400", description = "Numero nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = "{\"erro\":\"requisicao_invalida\",\"mensagem\":"
                            + "\"Valor invalido para o parametro 'numero': abc\"}")))
    @ApiResponse(responseCode = "404", description = "Nenhuma OS com esse numero",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    public OSResponse porNumero(
            @Parameter(description = "Numero da OS mostrado ao cliente", example = "15") @PathVariable Long numero) {
        var d = consultar.detalharPorNumero(numero);
        return OSResponse.detalhe(d.os(), d.linkPagamento(), d.estornoPendente());
    }

    @GetMapping("/status/{id}")
    @Operation(summary = "Consultar status da ordem de servico",
            description = "Status atual e data da ultima mudanca. Unica rota aberta ao perfil cliente: o cliente "
                    + "so ve as proprias OSs, e OS de outro cliente ou inexistente respondem 403 (igual, para nao "
                    + "revelar quais OSs existem). O perfil interno ve qualquer OS e recebe 404 se ela nao "
                    + "existir. Perfis: interno e cliente.")
    @ApiResponse(responseCode = "200", description = "Status da OS")
    @ApiResponse(responseCode = "400", description = "Id nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_ID_INVALIDO)))
    @ApiResponse(responseCode = "403", description = "Perfil cliente pedindo OS de outro cliente ou inexistente",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_403)))
    @ApiResponse(responseCode = "404", description = "OS nao existe (so para o perfil interno)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    public StatusResponse status(@Parameter(description = DESC_ID, example = "15") @PathVariable Long id,
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

    @GetMapping("/{id}/historico")
    @Operation(summary = "Historico da ordem de servico",
            description = "Todos os registros da OS em ordem cronologica: cada mudanca de status e os eventos "
                    + "relevantes da Saga (ex.: link gerado, pagamento atrasado, estorno), com o motivo quando "
                    + "houver. Perfil: interno.")
    @ApiResponse(responseCode = "200", description = "Historico, do mais antigo para o mais recente")
    @ApiResponse(responseCode = "400", description = "Id nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_ID_INVALIDO)))
    @ApiResponse(responseCode = "404", description = "OS nao existe",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    public List<HistoricoResponse> historico(@Parameter(description = DESC_ID, example = "15") @PathVariable Long id) {
        return consultar.historico(id).stream().map(HistoricoResponse::de).toList();
    }

    @PostMapping("/{id}/entregar")
    @Operation(summary = "Registrar entrega do veiculo",
            description = "Move a OS de CONCLUIDA para ENTREGUE (status final) e publica `os.entregue` em "
                    + "`os.eventos`. Sem corpo. Perfil: interno.")
    @ApiResponse(responseCode = "200", description = "Entrega registrada; retorna o novo status (ENTREGUE)")
    @ApiResponse(responseCode = "400", description = "Id nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_ID_INVALIDO)))
    @ApiResponse(responseCode = "404", description = "OS nao existe",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    @ApiResponse(responseCode = "409", description = "A OS nao esta CONCLUIDA (acao_recusada) ou foi alterada ao "
            + "mesmo tempo por outra operacao (conflito_concorrencia; tente de novo)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class), examples = {
                    @ExampleObject(name = "acao_recusada", value = "{\"erro\":\"acao_recusada\",\"mensagem\":"
                            + "\"So e possivel entregar uma OS concluida\"}"),
                    @ExampleObject(name = "conflito_concorrencia", value = EX_CONCORRENCIA)}))
    public StatusResponse entregar(@Parameter(description = DESC_ID, example = "15") @PathVariable Long id) {
        acionar(id, TipoGatilho.ENTREGA_SOLICITADA, "os.entregue",
                "So e possivel entregar uma OS concluida");
        return StatusResponse.de(consultar.buscar(id));
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar ordem de servico",
            description = "Cancela a OS antes do inicio da execucao (ABERTA, EM_DIAGNOSTICO, "
                    + "AGUARDANDO_APROVACAO, AGUARDANDO_PAGAMENTO ou PAGAMENTO_RECUSADO), com motivo "
                    + "CANCELAMENTO_SOLICITADO, e publica `os.cancelada` em `os.eventos`. Depois do pagamento "
                    + "confirmado (EM_EXECUCAO em diante) nao e possivel. Sem corpo. Perfil: interno.")
    @ApiResponse(responseCode = "200", description = "OS cancelada; retorna o novo status (CANCELADA)")
    @ApiResponse(responseCode = "400", description = "Id nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_ID_INVALIDO)))
    @ApiResponse(responseCode = "404", description = "OS nao existe",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    @ApiResponse(responseCode = "409", description = "A execucao ja comecou ou a OS ja terminou (acao_recusada), "
            + "ou houve alteracao concorrente (conflito_concorrencia; tente de novo)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class), examples = {
                    @ExampleObject(name = "acao_recusada", value = "{\"erro\":\"acao_recusada\",\"mensagem\":"
                            + "\"So e possivel cancelar uma OS antes do inicio da execucao\"}"),
                    @ExampleObject(name = "conflito_concorrencia", value = EX_CONCORRENCIA)}))
    public StatusResponse cancelar(@Parameter(description = DESC_ID, example = "15") @PathVariable Long id) {
        acionar(id, TipoGatilho.CANCELAMENTO_SOLICITADO, "os.cancelada",
                "So e possivel cancelar uma OS antes do inicio da execucao");
        return StatusResponse.de(consultar.buscar(id));
    }

    @PostMapping("/{id}/reprocessar-estorno")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reprocessar estorno pendente",
            description = "Tratamento manual de um estorno que falhou: reenvia `billing.estornar-pagamento` ao "
                    + "Billing para o estorno pendente da OS. Nao muda o status; o resultado chega depois por "
                    + "Kafka (`pagamento.estornado`). Use quando o detalhe mostrar `estornoPendente: true`. Sem "
                    + "corpo. Perfil: interno.")
    @ApiResponse(responseCode = "202", description = "Comando de estorno reenviado ao Billing",
            content = @Content(schema = @Schema(type = "object", description = "Confirmacao",
                    example = "{\"mensagem\":\"Estorno reenviado\"}")))
    @ApiResponse(responseCode = "400", description = "Id nao numerico",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_ID_INVALIDO)))
    @ApiResponse(responseCode = "404", description = "OS nao existe",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(value = EX_404)))
    @ApiResponse(responseCode = "409", description = "A OS nao tem estorno pendente (acao_recusada) ou houve "
            + "alteracao concorrente (conflito_concorrencia; tente de novo)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class), examples = {
                    @ExampleObject(name = "acao_recusada", value = "{\"erro\":\"acao_recusada\",\"mensagem\":"
                            + "\"A OS nao tem estorno pendente para reprocessar\"}"),
                    @ExampleObject(name = "conflito_concorrencia", value = EX_CONCORRENCIA)}))
    public Map<String, String> reprocessarEstorno(
            @Parameter(description = DESC_ID, example = "15") @PathVariable Long id) {
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
