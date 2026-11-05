package br.com.autocenterfiap.osservice.application.mensagem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.saga.Acao;
import br.com.autocenterfiap.osservice.domain.saga.Decisao;
import br.com.autocenterfiap.osservice.domain.saga.SagaOS;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

public class MensagemSagaFactory {

    private static final int VERSAO = 1;

    private final ObjectMapper mapper;
    private final Clock clock;
    private final int validadeMinutos;

    public MensagemSagaFactory(ObjectMapper mapper, Clock clock, int validadeMinutos) {
        this.mapper = mapper;
        this.clock = clock;
        this.validadeMinutos = validadeMinutos;
    }

    public MensagemSaida criar(Acao acao, OrdemServico os, SagaOS saga, Decisao decisao) {
        return switch (acao) {
            case ENVIAR_GERAR_ORCAMENTO -> {
                var p = mapper.createObjectNode();
                p.put("clienteId", os.getClienteId());
                p.set("itens", itens(os.getItens()));
                p.put("valorTotal", dinheiro(os.getValorTotal()));
                yield saida(Topicos.BILLING_COMANDOS, TiposMensagem.BILLING_GERAR_ORCAMENTO, os, p);
            }
            case ENVIAR_GERAR_COBRANCA -> {
                var p = mapper.createObjectNode();
                p.put("orcamentoId", saga.getOrcamentoId());
                p.put("valorTotal", dinheiro(os.getValorTotal()));
                p.put("tentativa", saga.getTentativasPagamento());
                p.put("validadeMinutos", validadeMinutos);
                yield saida(Topicos.BILLING_COMANDOS, TiposMensagem.BILLING_GERAR_COBRANCA, os, p);
            }
            case ENVIAR_INICIAR_REPARO -> {
                var p = mapper.createObjectNode();
                p.set("itens", itens(os.getItens()));
                yield saida(Topicos.EXECUCAO_COMANDOS, TiposMensagem.EXECUCAO_INICIAR_REPARO, os, p);
            }
            case ENVIAR_ESTORNO -> {
                var p = mapper.createObjectNode();
                p.put("pagamentoId", decisao.pagamentoAEstornar());
                p.put("motivo", decisao.motivoCancelamento());
                yield saida(Topicos.BILLING_COMANDOS, TiposMensagem.BILLING_ESTORNAR_PAGAMENTO, os, p);
            }
            case PUBLICAR_OS_CANCELADA -> {
                var p = mapper.createObjectNode();
                p.put("motivo", decisao.motivoCancelamento());
                yield saida(Topicos.OS_EVENTOS, TiposMensagem.OS_CANCELADA, os, p);
            }
            case PUBLICAR_OS_CONCLUIDA ->
                saida(Topicos.OS_EVENTOS, TiposMensagem.OS_CONCLUIDA, os, mapper.createObjectNode());
            case PUBLICAR_OS_ENTREGUE ->
                saida(Topicos.OS_EVENTOS, TiposMensagem.OS_ENTREGUE, os, mapper.createObjectNode());
            case ALERTAR_ESTORNO_FALHOU ->
                throw new IllegalArgumentException("A acao " + acao + " nao gera mensagem");
        };
    }

    public MensagemSaida osCriada(OrdemServico os) {
        var p = mapper.createObjectNode();
        p.put("numero", os.getNumero());
        p.put("clienteId", os.getClienteId());
        p.put("veiculoId", os.getVeiculoId());
        p.put("problemaRelatado", os.getProblemaRelatado());
        return saida(Topicos.OS_EVENTOS, TiposMensagem.OS_CRIADA, os, p);
    }

    private MensagemSaida saida(String topico, String tipo, OrdemServico os, ObjectNode payload) {
        var envelope = new MensagemEnvelope(UUID.randomUUID(), tipo, VERSAO, os.getId(),
                os.getCorrelationId(), clock.instant(), payload);
        return new MensagemSaida(topico, envelope);
    }

    private ObjectNode itens(ItensOS itens) {
        var raiz = mapper.createObjectNode();
        var servicos = raiz.putArray("servicos");
        itens.servicos().forEach(s -> {
            var n = servicos.addObject();
            n.put("servicoId", s.servicoId());
            n.put("descricao", s.descricao());
            n.put("valor", dinheiro(s.valor()));
        });
        var produtos = raiz.putArray("produtos");
        itens.produtos().forEach(pr -> {
            var n = produtos.addObject();
            n.put("produtoId", pr.produtoId());
            n.put("descricao", pr.descricao());
            n.put("quantidade", pr.quantidade());
            n.put("precoUnitario", dinheiro(pr.precoUnitario()));
        });
        return raiz;
    }

    private static String dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
