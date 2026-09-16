package com.lexintel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.lexintel.audit.repository")
@EnableNeo4jRepositories(
    basePackages = {
        "com.lexintel.contracts.repository",
        "com.lexintel.sec.repository"
    },
    transactionManagerRef = "neo4jTransactionManager"
)
public class LexIntelApplication {

    public static void main(String[] args) {
        SpringApplication.run(LexIntelApplication.class, args);
    }
}