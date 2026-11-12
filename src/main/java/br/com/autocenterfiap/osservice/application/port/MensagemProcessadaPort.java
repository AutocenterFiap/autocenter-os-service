package br.com.autocenterfiap.osservice.application.port;

import java.util.UUID;

public interface MensagemProcessadaPort {

    boolean registrarSeNova(UUID eventId);
}
