package br.com.autocenterfiap.osservice.adapter.out.cadastro;

import br.com.autocenterfiap.osservice.application.exception.CadastroIndisponivelException;
import br.com.autocenterfiap.osservice.application.port.CadastroPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Consulta o Cadastro Service por REST: 404 = nao existe; qualquer outra falha = indisponivel. */
@Component
@ConditionalOnProperty(name = "cadastro.modo", havingValue = "rest")
public class CadastroRestAdapter implements CadastroPort {

    private static final String ATIVO = "ATIVO";

    private final RestClient cliente;

    public CadastroRestAdapter(@Qualifier("cadastroRestClient") RestClient cliente) {
        this.cliente = cliente;
    }

    @Override
    public boolean clienteExiste(Long clienteId) {
        var cadastro = buscar("/v1/clientes/{id}", clienteId, ClienteCadastro.class);
        return cadastro != null && ATIVO.equals(cadastro.status());
    }

    @Override
    public boolean veiculoExiste(Long veiculoId) {
        return buscar("/v1/veiculos/{id}", veiculoId, VeiculoCadastro.class) != null;
    }

    private <T> T buscar(String caminho, Long id, Class<T> tipo) {
        try {
            return cliente.get().uri(caminho, id).retrieve().body(tipo);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new CadastroIndisponivelException("Cadastro indisponivel ao consultar " + caminho, e);
        }
    }

    record ClienteCadastro(Long id, String status) {
    }

    record VeiculoCadastro(Long id) {
    }
}
