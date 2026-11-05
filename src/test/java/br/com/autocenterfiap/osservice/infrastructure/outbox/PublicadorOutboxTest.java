package br.com.autocenterfiap.osservice.infrastructure.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.OutboxJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.OutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class PublicadorOutboxTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxJpaRepository repositorio = mock(OutboxJpaRepository.class);
    private final PublicadorOutbox publicador = new PublicadorOutbox(repositorio, kafka);

    private static OutboxJpaEntity mensagem(long id) {
        var e = new OutboxJpaEntity();
        e.setId(id);
        e.setTopico("os.eventos");
        e.setChave("k" + id);
        e.setConteudo("c" + id);
        return e;
    }

    @Test
    void falhaDeEnvioParaOLoteEMantemPendentes() {
        when(repositorio.findTop100ByEnviadoEmIsNullOrderByIdAsc()).thenReturn(List.of(mensagem(1), mensagem(2), mensagem(3)));
        when(kafka.send("os.eventos", "k1", "c1")).thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
        when(kafka.send("os.eventos", "k2", "c2")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker fora")));

        publicador.publicarPendentes();

        verify(repositorio).marcarEnviada(eq(1L), any(Instant.class));
        verify(repositorio, never()).marcarEnviada(eq(2L), any());
        verify(repositorio, never()).marcarEnviada(eq(3L), any());
        verify(kafka, never()).send("os.eventos", "k3", "c3");
    }

    @Test
    void semPendentesNaoEnviaNada() {
        when(repositorio.findTop100ByEnviadoEmIsNullOrderByIdAsc()).thenReturn(List.of());

        publicador.publicarPendentes();

        verify(kafka, never()).send(any(String.class), any(String.class), any(String.class));
    }
}
