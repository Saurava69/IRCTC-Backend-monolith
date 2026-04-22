package com.railway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.railway")
@EntityScan(basePackages = "com.railway")
@EnableJpaRepositories(basePackages = {
        "com.railway.booking.repository",
        "com.railway.payment.repository",
        "com.railway.train.repository",
        "com.railway.user.repository"
})
@EnableElasticsearchRepositories(basePackages = "com.railway.train.search.repository")
@EnableJpaAuditing
@EnableScheduling
public class RailwayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RailwayApplication.class, args);
    }
}
