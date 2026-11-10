package br.com.autocenterfiap.osservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.ContainersConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class OpenApiDocumentacaoIT {

    private static final List<String> METODOS = List.of("get", "post", "put", "patch", "delete");

    @Autowired
    private TestRestTemplate rest;

    private JsonNode doc;

    @BeforeEach
    void carregar() throws Exception {
        doc = new ObjectMapper().readTree(rest.getForObject("/v3/api-docs", String.class));
    }

    @Test
    void todasAsOperacoesTemResumoDescricaoTagERespostasDocumentadas() {
        var faltando = new ArrayList<String>();
        doc.path("paths").fields().forEachRemaining(caminho -> METODOS.forEach(metodo -> {
            var op = caminho.getValue().path(metodo);
            if (op.isMissingNode()) {
                return;
            }
            var nome = metodo.toUpperCase() + " " + caminho.getKey();
            if (op.path("summary").asText().isBlank()) {
                faltando.add(nome + ": summary");
            }
            if (op.path("description").asText().isBlank()) {
                faltando.add(nome + ": description");
            }
            if (!op.path("tags").toString().contains("Ordens de Servico")) {
                faltando.add(nome + ": tag");
            }
            for (var codigo : List.of("401", "403")) {
                if (op.path("responses").path(codigo).isMissingNode()) {
                    faltando.add(nome + ": resposta " + codigo);
                }
            }
            op.path("responses").fields().forEachRemaining(r -> {
                if (r.getValue().path("description").asText().isBlank()) {
                    faltando.add(nome + ": descricao da resposta " + r.getKey());
                }
                if (r.getKey().startsWith("4")
                        && !r.getValue().toString().contains("#/components/schemas/ErroResponse")) {
                    faltando.add(nome + ": resposta " + r.getKey() + " sem schema ErroResponse");
                }
            });
            op.path("parameters").forEach(p -> {
                if (p.path("description").asText().isBlank()) {
                    faltando.add(nome + ": parametro " + p.path("name").asText());
                }
                if (p.path("example").isMissingNode() && p.path("schema").path("example").isMissingNode()
                        && p.path("schema").path("default").isMissingNode()) {
                    faltando.add(nome + ": exemplo do parametro " + p.path("name").asText());
                }
            });
        }));
        assertThat(faltando).isEmpty();
    }

    @Test
    void todosOsSchemasECamposTemDescricao() {
        var faltando = new ArrayList<String>();
        doc.path("components").path("schemas").fields().forEachRemaining(schema -> {
            if (schema.getValue().path("description").asText().isBlank()) {
                faltando.add(schema.getKey());
            }
            schema.getValue().path("properties").fields().forEachRemaining(campo -> {
                if (!campo.getValue().has("$ref") && campo.getValue().path("description").asText().isBlank()) {
                    faltando.add(schema.getKey() + "." + campo.getKey());
                }
            });
        });
        assertThat(faltando).isEmpty();
    }

    @Test
    void cabecalhoDescreveAutenticacaoEOsCamposObrigatoriosDaAbertura() {
        assertThat(doc.path("info").path("description").asText()).contains("Bearer", "interno", "cliente");
        assertThat(doc.path("components").path("schemas").path("AbrirOSRequest").path("required").toString())
                .contains("clienteId", "veiculoId", "problemaRelatado");
        assertThat(doc.path("components").path("schemas").path("AbrirOSRequest").path("properties")
                .path("problemaRelatado").path("maxLength").asInt()).isEqualTo(500);
    }
}
