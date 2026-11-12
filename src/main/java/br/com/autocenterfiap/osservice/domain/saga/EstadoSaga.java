package br.com.autocenterfiap.osservice.domain.saga;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;

public record EstadoSaga(StatusOS status, int tentativasPagamento, Long pagamentoConfirmadoId,
        Long estornoPendenteId, int maxTentativas) {
}
