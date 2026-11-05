package br.com.autocenterfiap.osservice.application.exception;

public class OSJaEmAndamentoException extends RuntimeException {

    private final Long veiculoId;

    public OSJaEmAndamentoException(Long veiculoId) {
        super("Ja existe uma OS em andamento para o veiculo " + veiculoId);
        this.veiculoId = veiculoId;
    }

    public Long getVeiculoId() {
        return veiculoId;
    }
}
