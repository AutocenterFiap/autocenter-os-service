package br.com.autocenterfiap.osservice.adapter.out.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.autocenterfiap.osservice.application.exception.CadastroIndisponivelException;
import br.com.autocenterfiap.osservice.infrastructure.config.RepassaTokenInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CadastroRestAdapterTest {

    private MockRestServiceServer servidor;
    private CadastroRestAdapter adapter;

    @BeforeEach
    void preparar() {
        var builder = RestClient.builder().baseUrl("http://cadastro").requestInterceptor(new RepassaTokenInterceptor());
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new CadastroRestAdapter(builder.build());
    }

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void clienteCom(String status) {
        servidor.expect(requestTo("http://cadastro/v1/clientes/1"))
                .andRespond(withSuccess("{\"id\":1,\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void clienteAtivoExiste() {
        clienteCom("ATIVO");
        assertThat(adapter.clienteExiste(1L)).isTrue();
    }

    @Test
    void clienteInativoNaoExiste() {
        clienteCom("INATIVO");
        assertThat(adapter.clienteExiste(1L)).isFalse();
    }

    @Test
    void clienteInexistente404RetornaFalse() {
        servidor.expect(requestTo("http://cadastro/v1/clientes/9")).andRespond(withResourceNotFound());
        assertThat(adapter.clienteExiste(9L)).isFalse();
    }

    @Test
    void cadastro500LancaIndisponivel() {
        servidor.expect(requestTo("http://cadastro/v1/clientes/1")).andRespond(withServerError());
        assertThatThrownBy(() -> adapter.clienteExiste(1L)).isInstanceOf(CadastroIndisponivelException.class);
    }

    @Test
    void veiculoExiste() {
        servidor.expect(requestTo("http://cadastro/v1/veiculos/5"))
                .andRespond(withSuccess("{\"id\":5,\"placa\":\"ABC1D23\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.veiculoExiste(5L)).isTrue();
    }

    @Test
    void veiculoInexistente404RetornaFalse() {
        servidor.expect(requestTo("http://cadastro/v1/veiculos/5")).andRespond(withResourceNotFound());
        assertThat(adapter.veiculoExiste(5L)).isFalse();
    }

    @Test
    void veiculo500LancaIndisponivel() {
        servidor.expect(requestTo("http://cadastro/v1/veiculos/5")).andRespond(withServerError());
        assertThatThrownBy(() -> adapter.veiculoExiste(5L)).isInstanceOf(CadastroIndisponivelException.class);
    }

    @Test
    void repassaAuthorization() {
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer x");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        servidor.expect(requestTo("http://cadastro/v1/clientes/1")).andExpect(header("Authorization", "Bearer x"))
                .andRespond(withSuccess("{\"id\":1,\"status\":\"ATIVO\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.clienteExiste(1L)).isTrue();
        servidor.verify();
    }
}
