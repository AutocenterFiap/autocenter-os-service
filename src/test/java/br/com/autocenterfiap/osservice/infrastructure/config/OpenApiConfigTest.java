package br.com.autocenterfiap.osservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void semSchemasNaoFalha() {
        var openApi = new OpenAPI().components(new Components());

        assertThatCode(() -> config.documentarSchemasDeDominio().customise(openApi)).doesNotThrowAnyException();
    }

    @Test
    void descreveSchemaDeDominioEIgnoraCampoOuSchemaAusente() {
        var item = new ObjectSchema();
        item.addProperty("servicoId", new StringSchema());
        var semCampos = new ObjectSchema();
        var openApi = new OpenAPI().components(new Components()
                .addSchemas("ItemServico", item).addSchemas("ItemProduto", semCampos));

        config.documentarSchemasDeDominio().customise(openApi);

        assertThat(item.getDescription()).isEqualTo("Servico do orcamento");
        assertThat(item.getProperties().get("servicoId").getDescription()).isEqualTo("Id do servico no catalogo");
        assertThat(semCampos.getDescription()).isEqualTo("Produto do orcamento");
        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("ItensOS");
    }
}
