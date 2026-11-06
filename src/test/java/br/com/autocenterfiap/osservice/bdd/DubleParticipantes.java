package br.com.autocenterfiap.osservice.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.kafka.annotation.KafkaListener;

public class DubleParticipantes {

    private static final String GRUPO = "duble-participantes-bdd";

    private final ObjectMapper mapper;
    private final Map<Long, List<JsonNode>> porOs = new ConcurrentHashMap<>();

    public DubleParticipantes(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @KafkaListener(topics = {"billing.comandos", "execucao.comandos", "os.eventos"}, groupId = GRUPO)
    void receber(String conteudo) throws Exception {
        var envelope = mapper.readTree(conteudo);
        porOs.computeIfAbsent(envelope.get("osId").asLong(), id -> new CopyOnWriteArrayList<>()).add(envelope);
    }

    public List<JsonNode> mensagens(long osId, String tipo) {
        return porOs.getOrDefault(osId, List.of()).stream().filter(m -> tipo.equals(m.get("tipo").asText()))
                .toList();
    }
}
