package br.com.autocenterfiap.osservice.adapter.in.kafka;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.mensagem.EntradaSaga;
import br.com.autocenterfiap.osservice.application.mensagem.MensagemEnvelope;
import br.com.autocenterfiap.osservice.application.mensagem.TiposMensagem;
import br.com.autocenterfiap.osservice.application.port.MapeadorGatilhoPort;
import br.com.autocenterfiap.osservice.domain.entity.ItemProduto;
import br.com.autocenterfiap.osservice.domain.entity.ItemServico;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import br.com.autocenterfiap.osservice.domain.saga.TipoGatilho;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class MapeadorGatilho implements MapeadorGatilhoPort {

    private static final String ORCAMENTO_ID = "orcamentoId";
    private static final String PAGAMENTO_ID = "pagamentoId";
    private static final String URL = "url";

    @Override
    public Optional<EntradaSaga> mapear(MensagemEnvelope mensagem) {
        var leitor = new Leitor(mensagem.tipo(), mensagem.payload());
        return Optional.ofNullable(switch (mensagem.tipo()) {
            case TiposMensagem.DIAGNOSTICO_INICIADO -> simples(TipoGatilho.DIAGNOSTICO_INICIADO);
            case TiposMensagem.DIAGNOSTICO_CONCLUIDO -> diagnosticoConcluido(leitor);
            case TiposMensagem.ORCAMENTO_GERADO -> new EntradaSaga(Gatilho.de(TipoGatilho.ORCAMENTO_GERADO), null,
                    Map.of(ORCAMENTO_ID, leitor.id(ORCAMENTO_ID)));
            case TiposMensagem.ORCAMENTO_FALHOU -> simples(TipoGatilho.ORCAMENTO_FALHOU);
            case TiposMensagem.ORCAMENTO_APROVADO -> comOrcamento(TipoGatilho.ORCAMENTO_APROVADO, leitor);
            case TiposMensagem.ORCAMENTO_REPROVADO -> comOrcamento(TipoGatilho.ORCAMENTO_REPROVADO, leitor);
            case TiposMensagem.PAGAMENTO_LINK_GERADO -> linkGerado(leitor);
            case TiposMensagem.PAGAMENTO_CONFIRMADO -> pagamento(TipoGatilho.PAGAMENTO_CONFIRMADO, leitor, null);
            case TiposMensagem.PAGAMENTO_RECUSADO ->
                pagamento(TipoGatilho.PAGAMENTO_RECUSADO, leitor, leitor.inteiro("tentativa"));
            case TiposMensagem.PAGAMENTO_ESTORNADO -> pagamento(TipoGatilho.PAGAMENTO_ESTORNADO, leitor, null);
            case TiposMensagem.PAGAMENTO_ESTORNO_FALHOU ->
                pagamento(TipoGatilho.PAGAMENTO_ESTORNO_FALHOU, leitor, null);
            case TiposMensagem.EXECUCAO_INICIADA -> simples(TipoGatilho.EXECUCAO_INICIADA);
            case TiposMensagem.EXECUCAO_FINALIZADA -> simples(TipoGatilho.EXECUCAO_FINALIZADA);
            case TiposMensagem.EXECUCAO_FALHOU -> simples(TipoGatilho.EXECUCAO_FALHOU);
            default -> null;
        });
    }

    private static EntradaSaga simples(TipoGatilho tipo) {
        return new EntradaSaga(Gatilho.de(tipo), null, Map.of());
    }

    private static EntradaSaga comOrcamento(TipoGatilho tipo, Leitor leitor) {
        return new EntradaSaga(Gatilho.de(tipo), null, Map.of(ORCAMENTO_ID, leitor.id(ORCAMENTO_ID)));
    }

    private static EntradaSaga pagamento(TipoGatilho tipo, Leitor leitor, Integer tentativa) {
        var pagamentoId = leitor.id(PAGAMENTO_ID);
        return new EntradaSaga(Gatilho.pagamento(tipo, pagamentoId, tentativa, leitor.textoOpcional("motivo")), null,
                Map.of(PAGAMENTO_ID, pagamentoId));
    }

    private static EntradaSaga linkGerado(Leitor leitor) {
        var pagamentoId = leitor.id(PAGAMENTO_ID);
        var gatilho = Gatilho.pagamento(TipoGatilho.PAGAMENTO_LINK_GERADO, pagamentoId,
                leitor.inteiroOpcional("tentativa"), null);
        return new EntradaSaga(gatilho, null, Map.of(PAGAMENTO_ID, pagamentoId, URL, leitor.texto(URL)));
    }

    private static EntradaSaga diagnosticoConcluido(Leitor leitor) {
        var itens = leitor.no.path("itens");
        if (!itens.has("servicos") && !itens.has("produtos")) {
            throw leitor.invalido("itens");
        }
        var lidos = new ItensOS(leitor.lista(itens, "servicos", MapeadorGatilho::servico),
                leitor.lista(itens, "produtos", MapeadorGatilho::produto));
        return new EntradaSaga(Gatilho.diagnostico(lidos.vazio()), lidos, Map.of());
    }

    private static ItemServico servico(Leitor item) {
        return new ItemServico(item.id("servicoId"), item.texto("descricao"), item.dinheiro("valor"));
    }

    private static ItemProduto produto(Leitor item) {
        int quantidade = item.inteiro("quantidade");
        if (quantidade <= 0) {
            throw item.invalido("quantidade");
        }
        return new ItemProduto(item.id("produtoId"), item.texto("descricao"), quantidade,
                item.dinheiro("precoUnitario"));
    }

    private static final class Leitor {

        private final String tipo;
        private final JsonNode no;

        private Leitor(String tipo, JsonNode no) {
            this.tipo = tipo;
            this.no = no == null ? MissingNode.getInstance() : no;
        }

        private Long id(String campo) {
            var valor = no.path(campo);
            if (!valor.isIntegralNumber() || !valor.canConvertToLong()) {
                throw invalido(campo);
            }
            return valor.longValue();
        }

        private int inteiro(String campo) {
            var valor = no.path(campo);
            if (!valor.isIntegralNumber() || !valor.canConvertToInt()) {
                throw invalido(campo);
            }
            return valor.intValue();
        }

        private Integer inteiroOpcional(String campo) {
            return no.path(campo).canConvertToInt() ? no.path(campo).intValue() : null;
        }

        private String texto(String campo) {
            var valor = no.path(campo);
            if (!valor.isTextual() || valor.textValue().isBlank()) {
                throw invalido(campo);
            }
            return valor.textValue();
        }

        private String textoOpcional(String campo) {
            return no.path(campo).isTextual() ? no.path(campo).textValue() : null;
        }

        private BigDecimal dinheiro(String campo) {
            try {
                var valor = new BigDecimal(texto(campo));
                if (valor.signum() < 0) {
                    throw invalido(campo);
                }
                return valor;
            } catch (NumberFormatException e) {
                throw invalido(campo);
            }
        }

        private <T> List<T> lista(JsonNode pai, String campo, Function<Leitor, T> leitura) {
            var valor = pai.path(campo);
            if (valor.isMissingNode()) {
                return List.of();
            }
            if (!valor.isArray()) {
                throw invalido(campo);
            }
            var lidos = new ArrayList<T>();
            valor.forEach(item -> lidos.add(leitura.apply(new Leitor(tipo, item))));
            return lidos;
        }

        private MensagemInvalidaException invalido(String campo) {
            return new MensagemInvalidaException("Mensagem " + tipo + " com campo ausente ou invalido: " + campo);
        }
    }
}
