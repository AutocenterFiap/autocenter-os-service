package br.com.autocenterfiap.osservice.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("AutoCenter - OS Service").version("1.0.0")
                        .description("Abertura e acompanhamento das ordens de serviço. Orquestra a Saga "
                                + "coreografada com Billing e Execução por eventos Kafka e consulta clientes, "
                                + "veículos e catálogo no serviço de Cadastro."))
                .components(new Components().addSecuritySchemes(ESQUEMA,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("JWT emitido pelo serviço de autenticação. Funcionários usam o perfil "
                                        + "interno; clientes acessam apenas a consulta de status das próprias OS.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
