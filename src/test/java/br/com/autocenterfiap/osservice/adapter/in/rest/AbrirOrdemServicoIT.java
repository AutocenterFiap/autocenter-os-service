package br.com.autocenterfiap.osservice.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.TokensTeste;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class AbrirOrdemServicoIT {

    private static final String URL = "/v1/ordem-servicos";

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void autenticar() {
        TokensTeste.usar(rest, TokensTeste.administrativo());
    }

    private Map<String, Object> corpo(long veiculoId, String problema) {
        return Map.of("clienteId", 1, "veiculoId", veiculoId, "problemaRelatado", problema);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retorna201ComStatusAberta() {
        var resposta = rest.postForEntity(URL, corpo(1001L, "Barulho no motor"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var json = (Map<String, Object>) resposta.getBody();
        assertThat(json.get("status")).isEqualTo("ABERTA");
        assertThat(json.get("id")).isNotNull();
        assertThat(json.get("numero")).isNotNull();
        assertThat(json.get("veiculoId")).isEqualTo(1001);
        assertThat(json.get("criadaEm")).isNotNull();
        var id = ((Number) json.get("id")).longValue();
        assertThat(jdbc.queryForObject("select count(*) from saga_os where os_id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from os_historico where os_id = ?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void problemaEmBrancoRetorna400() {
        var resposta = rest.postForEntity(URL, corpo(1002L, "  "), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map<String, Object>) resposta.getBody())).containsKeys("erro", "mensagem");
    }

    @Test
    @SuppressWarnings("unchecked")
    void veiculoComOSEmAndamentoRetorna409() {
        assertThat(rest.postForEntity(URL, corpo(1003L, "Primeira"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        var segunda = rest.postForEntity(URL, corpo(1003L, "Segunda"), Map.class);

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((Map<String, Object>) segunda.getBody())).containsKeys("erro", "mensagem");
        assertThat(jdbc.queryForObject("select count(*) from ordem_servico where veiculo_id = 1003", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void duasAberturasSimultaneasCriamUmaSo() throws Exception {
        var largada = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> chamada = () -> {
                largada.await();
                return HttpStatus.valueOf(
                        rest.postForEntity(URL, corpo(1004L, "Concorrente"), Map.class).getStatusCode().value());
            };
            Future<HttpStatus> a = pool.submit(chamada);
            Future<HttpStatus> b = pool.submit(chamada);
            largada.countDown();

            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from ordem_servico where veiculo_id = 1004", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from os_historico h join ordem_servico o on o.id = h.os_id where o.veiculo_id = 1004",
                Long.class)).isEqualTo(1L);
    }
}
