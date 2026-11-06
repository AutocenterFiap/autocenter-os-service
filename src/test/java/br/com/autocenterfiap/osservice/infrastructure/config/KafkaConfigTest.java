package br.com.autocenterfiap.osservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.autocenterfiap.osservice.application.exception.MensagemInvalidaException;
import br.com.autocenterfiap.osservice.application.port.ObservabilidadePort;
import com.fasterxml.jackson.core.JsonParseException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SuppressWarnings("unchecked")
class KafkaConfigTest {

    private final KafkaOperations<Object, Object> template = mock(KafkaOperations.class);
    private final Consumer<String, String> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final ConsumerRecord<String, String> registro =
            new ConsumerRecord<>("billing.eventos", 2, 40L, "10", "conteudo");
    private final ObservabilidadePort observabilidade = mock(ObservabilidadePort.class);
    private DefaultErrorHandler tratador;

    @BeforeEach
    void preparar() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(consumer.partitionsFor(eq("billing.eventos.dlt"), any(Duration.class))).thenReturn(List.of(
                new PartitionInfo("billing.eventos.dlt", 0, null, null, null),
                new PartitionInfo("billing.eventos.dlt", 1, null, null, null),
                new PartitionInfo("billing.eventos.dlt", 2, null, null, null)));
        tratador = new KafkaConfig().tratadorDeErrosKafka(template, observabilidade);
    }

    private void falhar(Exception causa) {
        tratador.handleRemaining(new ListenerExecutionFailedException("falhou", causa), List.of(registro),
                consumer, container);
    }

    private ProducerRecord<Object, Object> enviado() {
        var captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void jsonInvalidoVaiDiretoParaDltNaMesmaParticao() {
        falhar(new JsonParseException(null, "json ruim"));

        assertThat(enviado().topic()).isEqualTo("billing.eventos.dlt");
        assertThat(enviado().partition()).isEqualTo(2);
        assertThat(enviado().value()).isEqualTo("conteudo");
        assertThat(enviado().key()).isEqualTo("10");
    }

    @Test
    void mensagemEnviadaParaDltIncrementaAMetricaEAindaChegaNela() {
        falhar(new MensagemInvalidaException("pagamento.confirmado sem pagamentoId"));

        verify(observabilidade).mensagemDlt();
        assertThat(enviado().topic()).isEqualTo("billing.eventos.dlt");
    }

    @Test
    void mensagemInvalidaVaiDiretoParaDlt() {
        falhar(new MensagemInvalidaException("pagamento.confirmado sem pagamentoId"));

        assertThat(enviado().topic()).isEqualTo("billing.eventos.dlt");
    }

    @Test
    void conflitoOtimistaERepetidoTresVezesEDepoisVaiParaDlt() {
        var conflito = new ObjectOptimisticLockingFailureException("OrdemServico", 10L);

        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            assertThatThrownBy(() -> falhar(conflito)).as("retentativa " + tentativa).isNotNull();
            verify(template, never()).send(any(ProducerRecord.class));
            verify(observabilidade, never()).mensagemDlt();
        }
        falhar(conflito);

        verify(observabilidade).mensagemDlt();
        assertThat(enviado().topic()).isEqualTo("billing.eventos.dlt");
    }

    @Test
    void intervaloCresceDeUmSegundoComMultiplicadorDois() {
        var backOff = KafkaConfig.intervaloDeRetentativas();

        assertThat(backOff.getInitialInterval()).isEqualTo(1000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
        assertThat(backOff.getMaxRetries()).isEqualTo(3);
    }
}
