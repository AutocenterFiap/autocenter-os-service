package br.com.autocenterfiap.osservice.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroJwt extends OncePerRequestFilter {

    static final String ISSUER = "Auto Center Fiap";
    static final String ROLE_CLIENTE = "ROLE_CLIENTE";
    static final String ROLE_INTERNO = "ROLE_INTERNO";
    private static final String PREFIXO = "Bearer ";
    private static final Logger LOG = LoggerFactory.getLogger(FiltroJwt.class);

    private final JWTVerifier verificador;

    public FiltroJwt(String segredo) {
        this.verificador = JWT.require(Algorithm.HMAC256(segredo)).withIssuer(ISSUER).withClaimPresence("exp").build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cabecalho = request.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith(PREFIXO)) {
            try {
                var usuario = paraUsuario(verificador.verify(cabecalho.substring(PREFIXO.length()).trim()));
                var autoridade = usuario.cliente() ? ROLE_CLIENTE : ROLE_INTERNO;
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority(autoridade))));
            } catch (JWTVerificationException e) {
                LOG.debug("Token JWT rejeitado: {}", e.getClass().getSimpleName());
            }
        }
        chain.doFilter(request, response);
    }

    private static UsuarioToken paraUsuario(DecodedJWT jwt) {
        var cliente = "cliente".equals(jwt.getClaim("tipo").asString());
        var claimId = jwt.getClaim("clienteId");
        Long clienteId = null;
        if (cliente && !claimId.isNull() && !claimId.isMissing()) {
            clienteId = claimId.asLong();
        }
        return new UsuarioToken(jwt.getSubject(), cliente, clienteId);
    }
}
