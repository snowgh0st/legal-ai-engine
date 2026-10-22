package com.lexintel.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

/**
 * Registers the two transaction managers the app needs because it writes to two different
 * databases: Postgres (JPA, audit logs) and Neo4j (contract/SEC graphs). {@code
 * transactionManager} is {@code @Primary} so plain {@code @Transactional} defaults to Postgres;
 * anything touching the graph must explicitly request {@code @Transactional("neo4jTransactionManager")}
 * (see {@link com.lexintel.sec.service.FilingGraphExtractionService#processAndPersistFiling}).
 * A single {@code @Transactional} method cannot atomically span both — see the note on
 * {@link com.lexintel.contracts.service.ContractIngestionService} for the practical
 * consequence of that.
 */
@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    // Primary Transaction Manager for JPA / PostgreSQL
    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // Explicit Transaction Manager for Neo4j Graph DB
    @Bean(name = "neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
        return new Neo4jTransactionManager(driver);
    }
}