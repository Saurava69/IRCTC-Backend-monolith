package com.railway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.railway")
@EntityScan(basePackages = "com.railway")
@EnableJpaRepositories(basePackages = "com.railway")
@EnableJpaAuditing
public class RailwayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailwayApplication.class, args);
    }
}
