package br.com.autocenterfiap.osservice.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.TokensTeste;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sistema.seguranca.chave.secreta=chave-de-teste", "cadastro.modo=rest",
                "cadastro.url=http://127.0.0.1:1"})
@Import(ContainersConfig.class)
class CadastroIndisponivelIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void cadastroIndisponivelRetorna503ENaoGravaOs() {
        TokensTeste.usar(rest, TokensTeste.administrativo());
        var antes = jdbc.queryForObject("select count(*) from ordem_servico", Long.class);

        var resposta = rest.postForEntity("/v1/ordem-servicos",
                Map.of("clienteId", 1, "veiculoId", 77_001, "problemaRelatado", "Freio fazendo barulho"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat((Map<String, Object>) resposta.getBody()).containsEntry("erro", "cadastro_indisponivel");
        assertThat(jdbc.queryForObject("select count(*) from ordem_servico", Long.class)).isEqualTo(antes);
    }
}
