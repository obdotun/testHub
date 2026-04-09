package com.testhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // pour l'exécution asynchrone des tests
public class TestHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestHubApplication.class, args);
    }

}
