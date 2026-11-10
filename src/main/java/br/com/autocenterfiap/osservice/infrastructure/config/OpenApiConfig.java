package br.com.autocenterfiap.osservice.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";

    private static final String DESCRICAO = """
            Orquestrador da Saga das ordens de servico (OS) do AutoCenter. Abre a OS e conduz o fluxo \
            diagnostico -> orcamento -> pagamento -> execucao -> entrega, comandando o Billing e a Execucao por \
            Kafka. As mudancas de status vindas dos participantes chegam por Kafka; esta API so abre, consulta \
            e aplica as acoes manuais (entregar, cancelar, reprocessar estorno).

            **Autenticacao:** JWT HS256 no cabecalho `Authorization: Bearer <token>`, emitido com issuer \
            `Auto Center Fiap`. Perfis:
            - **interno** (atendente/sistema; claim `tipo` diferente de `cliente`): acesso a todas as rotas.
            - **cliente** (claim `tipo=cliente` e `clienteId`): so `GET /v1/ordem-servicos/status/{id}`, e so \
            das proprias OSs.

            Para testar, gere um token com `scripts/gerar-token.sh` e use o botao **Authorize**.

            **Erros** seguem sempre o formato `ErroResponse` (`erro` + `mensagem`).""";

    private static final Map<String, Map<String, String>> CAMPOS_DOMINIO = Map.of(
            "ItensOS", Map.of(
                    "", "Itens definidos no diagnostico. Listas vazias ate o diagnostico terminar",
                    "servicos", "Servicos (mao de obra) a executar",
                    "produtos", "Pecas e produtos a usar"),
            "ItemServico", Map.of(
                    "", "Servico do orcamento",
                    "servicoId", "Id do servico no catalogo",
                    "descricao", "Descricao do servico",
                    "valor", "Valor do servico"),
            "ItemProduto", Map.of(
                    "", "Produto do orcamento",
                    "produtoId", "Id do produto no estoque",
                    "descricao", "Descricao do produto",
                    "quantidade", "Quantidade usada",
                    "precoUnitario", "Preco por unidade; o total do item e quantidade x precoUnitario"));

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("AutoCenter - OS Service").version("1.0.0").description(DESCRICAO))
                .components(new Components().addSecuritySchemes(ESQUEMA,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("JWT HS256 (issuer `Auto Center Fiap`), perfil interno ou cliente")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }

    @Bean
    OpenApiCustomizer documentarSchemasDeDominio() {
        return openApi -> {
            var schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            CAMPOS_DOMINIO.forEach((nome, campos) -> {
                Schema<?> schema = schemas.get(nome);
                if (schema == null) {
                    return;
                }
                campos.forEach((campo, descricao) -> {
                    if (campo.isEmpty()) {
                        schema.setDescription(descricao);
                    } else if (schema.getProperties() != null && schema.getProperties().containsKey(campo)) {
                        schema.getProperties().get(campo).setDescription(descricao);
                    }
                });
            });
        };
    }
}
