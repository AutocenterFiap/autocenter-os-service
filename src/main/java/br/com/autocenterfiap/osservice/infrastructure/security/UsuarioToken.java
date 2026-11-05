package br.com.autocenterfiap.osservice.infrastructure.security;

public record UsuarioToken(String subject, boolean cliente, Long clienteId) {
}
