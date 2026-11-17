package br.com.autocenterfiap.osservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RepassaTokenInterceptorTest {

    private final RepassaTokenInterceptor interceptor = new RepassaTokenInterceptor();

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void semRequisicaoWebNaoRepassaToken() throws Exception {
        var saida = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://cadastro/v1/clientes/1"));

        interceptor.intercept(saida, new byte[0], (r, b) -> new MockClientHttpResponse());

        assertThat(saida.getHeaders().containsKey("Authorization")).isFalse();
    }

    @Test
    void requisicaoSemAuthorizationNaoRepassa() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        var saida = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://cadastro/v1/clientes/1"));

        interceptor.intercept(saida, new byte[0], (r, b) -> new MockClientHttpResponse());

        assertThat(saida.getHeaders().containsKey("Authorization")).isFalse();
    }

    @Test
    void repassaAuthorizationDaRequisicaoAtual() throws Exception {
        var entrada = new MockHttpServletRequest();
        entrada.addHeader("Authorization", "Bearer abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrada));
        var saida = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://cadastro/v1/clientes/1"));

        interceptor.intercept(saida, new byte[0], (r, b) -> new MockClientHttpResponse());

        assertThat(saida.getHeaders().getFirst("Authorization")).isEqualTo("Bearer abc");
    }
}
