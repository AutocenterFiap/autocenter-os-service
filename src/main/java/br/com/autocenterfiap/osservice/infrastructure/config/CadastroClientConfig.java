package br.com.autocenterfiap.osservice.infrastructure.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "cadastro.modo", havingValue = "rest")
public class CadastroClientConfig {

    @Bean
    RestClient cadastroRestClient(RestClient.Builder builder, @Value("${cadastro.url}") String url) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        return builder.baseUrl(url).requestFactory(fabrica).requestInterceptor(new RepassaTokenInterceptor()).build();
    }
}
