package br.com.autocenterfiap.osservice.bdd;

import br.com.autocenterfiap.osservice.ContainersConfig;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@CucumberContextConfiguration
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import({ContainersConfig.class, DubleParticipantes.class})
public class ConfiguracaoCucumber {
}
