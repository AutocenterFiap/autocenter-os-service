package br.com.autocenterfiap.osservice.domain.saga;

public record Gatilho(TipoGatilho tipo, Long pagamentoId, Integer tentativa, String motivo, boolean semItens) {

    public static Gatilho de(TipoGatilho tipo) {
        return new Gatilho(tipo, null, null, null, false);
    }

    public static Gatilho pagamento(TipoGatilho tipo, Long pagamentoId, Integer tentativa, String motivo) {
        return new Gatilho(tipo, pagamentoId, tentativa, motivo, false);
    }

    public static Gatilho diagnostico(boolean semItens) {
        return new Gatilho(TipoGatilho.DIAGNOSTICO_CONCLUIDO, null, null, null, semItens);
    }
}
