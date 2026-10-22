package com.lexintel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Spring Boot entry point. LexIntel runs two separate persistence stores side by side:
 * Postgres/JPA for the compliance audit trail ({@code audit.repository}), and Neo4j for the
 * contract + SEC knowledge graphs ({@code contracts.repository}, {@code sec.repository}).
 * The two repository families must stay explicitly scoped like this (rather than one blanket
 * {@code @EnableJpaRepositories}) or Spring will try to back Neo4j repositories with JPA and
 * fail to start. See {@link com.lexintel.config.DatabaseConfig} for the matching transaction
 * manager wiring.
 */
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