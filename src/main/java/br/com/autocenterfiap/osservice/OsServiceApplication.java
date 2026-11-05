package br.com.autocenterfiap.osservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OsServiceApplication.class, args);
    }
}
