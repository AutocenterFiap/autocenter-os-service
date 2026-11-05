package br.com.autocenterfiap.osservice.domain.saga;

import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import java.util.List;

public record Decisao(boolean aceita, List<StatusOS> transicoes, List<Acao> acoes, Long pagamentoAEstornar,
        String motivoCancelamento, boolean limparEstornoPendente) {

    public Decisao {
        transicoes = List.copyOf(transicoes);
        acoes = List.copyOf(acoes);
    }

    public static Decisao ignorar() {
        return new Decisao(false, List.of(), List.of(), null, null, false);
    }

    static Decisao aceitar(List<StatusOS> transicoes, List<Acao> acoes) {
        return new Decisao(true, transicoes, acoes, null, null, false);
    }

    static Decisao cancelar(List<StatusOS> transicoes, String motivo) {
        return new Decisao(true, transicoes, List.of(Acao.PUBLICAR_OS_CANCELADA), null, motivo, false);
    }

    static Decisao estornar(List<StatusOS> transicoes, Long pagamentoId, String motivo) {
        return new Decisao(true, transicoes, List.of(Acao.ENVIAR_ESTORNO), pagamentoId, motivo, false);
    }
}
