package br.com.autocenterfiap.osservice;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.util.Date;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;

public final class TokensTeste {

    public static final String SEGREDO = "chave-de-teste";
    public static final String ISSUER = "Auto Center Fiap";

    private TokensTeste() {
    }

    public static String administrativo() {
        return administrativo(SEGREDO, ISSUER, Instant.now().plusSeconds(3600));
    }

    public static String administrativo(String segredo, String issuer, Instant expira) {
        return JWT.create().withIssuer(issuer).withSubject("admin").withExpiresAt(Date.from(expira))
                .sign(Algorithm.HMAC256(segredo));
    }

    public static String cliente(Long clienteId) {
        var jwt = JWT.create().withIssuer(ISSUER).withSubject("12345678901").withClaim("tipo", "cliente")
                .withClaim("nome", "Cliente Teste").withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)));
        if (clienteId != null) {
            jwt.withClaim("clienteId", clienteId);
        }
        return jwt.sign(Algorithm.HMAC256(SEGREDO));
    }

    public static void usar(TestRestTemplate rest, String token) {
        ClientHttpRequestInterceptor interceptor = (req, corpo, exec) -> {
            if (token != null) {
                req.getHeaders().setBearerAuth(token);
            }
            return exec.execute(req, corpo);
        };
        rest.getRestTemplate().setInterceptors(java.util.List.of(interceptor));
    }
}
