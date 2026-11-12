package br.com.autocenterfiap.osservice.adapter.out.cadastro;

import br.com.autocenterfiap.osservice.application.port.CadastroPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cadastro.modo", havingValue = "stub")
public class CadastroStubAdapter implements CadastroPort {

    @Override
    public boolean clienteExiste(Long clienteId) {
        return true;
    }

    @Override
    public boolean veiculoExiste(Long veiculoId) {
        return true;
    }
}
