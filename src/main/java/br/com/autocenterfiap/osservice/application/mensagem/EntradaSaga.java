package br.com.autocenterfiap.osservice.application.mensagem;

import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.saga.Gatilho;
import java.util.Map;

public record EntradaSaga(Gatilho gatilho, ItensOS itens, Map<String, Object> dados) {
}
