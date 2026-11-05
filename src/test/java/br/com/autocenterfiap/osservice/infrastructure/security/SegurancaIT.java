package br.com.autocenterfiap.osservice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.TokensTeste;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
@AutoConfigureObservability
class SegurancaIT {

    private static final String URL = "/v1/ordem-servicos";
    private static final AtomicLong VEICULO = new AtomicLong(80_000);
    private static final long CLIENTE = 501L;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private OrdemServicoRepositoryPort ordens;

    @BeforeEach
    void limparInterceptors() {
        TokensTeste.usar(rest, null);
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private OrdemServico criar(long clienteId) {
        return ordens.salvar(OrdemServico.abrir(clienteId, VEICULO.incrementAndGet(), "Problema",
                UUID.randomUUID(), Instant.now()));
    }

    private ResponseEntity<Object> chamar(HttpMethod metodo, String caminho, String token, Object corpo) {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return rest.exchange(caminho, metodo, new HttpEntity<>(corpo, h), Object.class);
    }

    @SuppressWarnings("unchecked")
    private void assertErro(ResponseEntity<Object> r, HttpStatus status) {
        assertThat(r.getStatusCode()).as(String.valueOf(r.getBody())).isEqualTo(status);
        assertThat((Map<String, Object>) r.getBody()).containsKeys("erro", "mensagem");
    }

    @Test
    void semTokenRetorna401() {
        assertErro(chamar(HttpMethod.GET, URL, null, null), HttpStatus.UNAUTHORIZED);
        assertErro(chamar(HttpMethod.POST, URL, null, Map.of()), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenExpiradoOuAssinaturaErradaOuIssuerErradoRetorna401() {
        var agora = Instant.now();
        var expirado = TokensTeste.administrativo(TokensTeste.SEGREDO, TokensTeste.ISSUER, agora.minusSeconds(60));
        var assinaturaErrada = TokensTeste.administrativo("outro-segredo", TokensTeste.ISSUER, agora.plusSeconds(600));
        var issuerErrado = TokensTeste.administrativo(TokensTeste.SEGREDO, "Outro Emissor", agora.plusSeconds(600));

        for (var token : List.of(expirado, assinaturaErrada, issuerErrado, "lixo.nao.jwt")) {
            assertErro(chamar(HttpMethod.GET, URL, token, null), HttpStatus.UNAUTHORIZED);
        }
        assertThat(chamar(HttpMethod.GET, URL, TokensTeste.administrativo(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void tokenAdministrativoAcessaTodasAsRotas() {
        var os = criar(CLIENTE);
        var t = TokensTeste.administrativo();

        assertThat(chamar(HttpMethod.GET, URL, t, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, URL + "/" + os.getId(), t, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, URL + "/numero/" + os.getNumero(), t, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, URL + "/status/" + os.getId(), t, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.GET, URL + "/" + os.getId() + "/historico", t, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.POST, URL, t,
                Map.of("clienteId", 1, "veiculoId", VEICULO.incrementAndGet(), "problemaRelatado", "x"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(chamar(HttpMethod.POST, URL + "/" + os.getId() + "/cancelar", t, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(HttpMethod.POST, URL + "/" + os.getId() + "/entregar", t, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(chamar(HttpMethod.POST, URL + "/" + os.getId() + "/reprocessar-estorno", t, null)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void tokenClienteVeStatusDaPropriaOS() {
        var os = criar(CLIENTE);

        var r = chamar(HttpMethod.GET, URL + "/status/" + os.getId(), TokensTeste.cliente(CLIENTE), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) r.getBody()).get("status")).isEqualTo("ABERTA");
    }

    @Test
    void tokenClienteNaoVeStatusDeOutraOS() {
        var os = criar(CLIENTE);

        assertErro(chamar(HttpMethod.GET, URL + "/status/" + os.getId(), TokensTeste.cliente(CLIENTE + 1), null),
                HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.GET, URL + "/status/999999", TokensTeste.cliente(CLIENTE), null),
                HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.GET, URL + "/status/" + os.getId(), TokensTeste.cliente(null), null),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenClienteNaoAcessaDemaisRotas() {
        var os = criar(CLIENTE);
        var t = TokensTeste.cliente(CLIENTE);
        var id = "/" + os.getId();

        assertErro(chamar(HttpMethod.POST, URL, t,
                Map.of("clienteId", CLIENTE, "veiculoId", 1, "problemaRelatado", "x")), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.GET, URL, t, null), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.GET, URL + id, t, null), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.GET, URL + "/numero/" + os.getNumero(), t, null), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.GET, URL + id + "/historico", t, null), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.POST, URL + id + "/cancelar", t, null), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.POST, URL + id + "/entregar", t, null), HttpStatus.FORBIDDEN);
        assertErro(chamar(HttpMethod.POST, URL + id + "/reprocessar-estorno", t, null), HttpStatus.FORBIDDEN);
    }

    @Test
    void actuatorESwaggerSaoPublicos() {
        for (var caminho : List.of("/actuator/health/liveness", "/actuator/health/readiness",
                "/actuator/prometheus", "/v3/api-docs", "/swagger-ui/index.html")) {
            assertThat(rest.getForEntity(caminho, String.class).getStatusCode()).as(caminho)
                    .isEqualTo(HttpStatus.OK);
        }
    }
}
