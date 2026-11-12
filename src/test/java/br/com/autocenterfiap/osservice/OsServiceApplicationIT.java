package br.com.autocenterfiap.osservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class OsServiceApplicationIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void contextoSobe() {
        var resposta = rest.getForEntity("/actuator/health/liveness", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
