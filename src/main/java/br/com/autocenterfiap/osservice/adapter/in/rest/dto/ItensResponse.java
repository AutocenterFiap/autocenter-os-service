package br.com.autocenterfiap.osservice.adapter.in.rest.dto;

import br.com.autocenterfiap.osservice.domain.entity.ItemProduto;
import br.com.autocenterfiap.osservice.domain.entity.ItemServico;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Itens definidos no diagnóstico, com descrição e preço vindos do Cadastro")
public record ItensResponse(
        @Schema(description = "Serviços que serão executados") List<Servico> servicos,
        @Schema(description = "Produtos e insumos utilizados") List<Produto> produtos) {

    public static ItensResponse de(ItensOS itens) {
        if (itens == null) {
            return null;
        }
        return new ItensResponse(itens.servicos().stream().map(Servico::de).toList(),
                itens.produtos().stream().map(Produto::de).toList());
    }

    @Schema(name = "ItemServicoResponse", description = "Serviço da ordem de serviço")
    public record Servico(
            @Schema(description = "Identificador do serviço no Cadastro", example = "3") Long servicoId,
            @Schema(description = "Descrição do serviço", example = "Troca de pastilhas de freio") String descricao,
            @Schema(description = "Valor do serviço", example = "180.00") BigDecimal valor) {

        static Servico de(ItemServico item) {
            return new Servico(item.servicoId(), item.descricao(), item.valor());
        }
    }

    @Schema(name = "ItemProdutoResponse", description = "Produto ou insumo da ordem de serviço")
    public record Produto(
            @Schema(description = "Identificador do produto no Cadastro", example = "7") Long produtoId,
            @Schema(description = "Descrição do produto", example = "Pastilha de freio dianteira") String descricao,
            @Schema(description = "Quantidade utilizada", example = "2") int quantidade,
            @Schema(description = "Preço unitário", example = "129.95") BigDecimal precoUnitario) {

        static Produto de(ItemProduto item) {
            return new Produto(item.produtoId(), item.descricao(), item.quantidade(), item.precoUnitario());
        }
    }
}
