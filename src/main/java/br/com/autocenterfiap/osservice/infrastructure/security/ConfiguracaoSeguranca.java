package br.com.autocenterfiap.osservice.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class ConfiguracaoSeguranca {

    @Bean
    SecurityFilterChain filtroSeguranca(HttpSecurity http,
            @Value("${sistema.seguranca.chave.secreta}") String segredo, ObjectMapper mapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/ordem-servicos/status/*")
                                .hasAnyAuthority(FiltroJwt.ROLE_CLIENTE, FiltroJwt.ROLE_INTERNO)
                        .requestMatchers("/v1/ordem-servicos/**").hasAuthority(FiltroJwt.ROLE_INTERNO)
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escrever(mapper, res, HttpStatus.UNAUTHORIZED,
                                "nao_autenticado", "Token ausente, invalido ou expirado"))
                        .accessDeniedHandler((req, res, ex) -> escrever(mapper, res, HttpStatus.FORBIDDEN,
                                "acesso_negado", "Acesso negado a este recurso")))
                .addFilterBefore(new FiltroJwt(segredo), BasicAuthenticationFilter.class)
                .build();
    }

    private static void escrever(ObjectMapper mapper, HttpServletResponse res, HttpStatus status, String erro,
            String mensagem) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), Map.of("erro", erro, "mensagem", mensagem));
    }
}
