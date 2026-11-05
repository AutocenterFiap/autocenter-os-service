package br.com.autocenterfiap.osservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PerfilDemoTest {

    private static final String VALIDADE = "saga.pagamento.validade-minutos";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void padraoUsaTrintaMinutos() {
        runner.run(ctx -> assertThat(ctx.getEnvironment().getProperty(VALIDADE)).isEqualTo("30"));
    }

    @Test
    void perfilDemoUsaDoisMinutos() {
        runner.withPropertyValues("spring.profiles.active=demo")
                .run(ctx -> assertThat(ctx.getEnvironment().getProperty(VALIDADE)).isEqualTo("2"));
    }
}
