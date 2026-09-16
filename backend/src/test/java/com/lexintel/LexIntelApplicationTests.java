package com.lexintel;

import com.lexintel.contracts.repository.ContractRepository;
import com.lexintel.audit.repository.DocumentAuditLogRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
    // Disable external service auto-configurations during unit test execution
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveRepositoriesAutoConfiguration",
    "spring.ai.openai.api-key=sk-test-mock-key-12345"
})
@TestPropertySource(properties = {
    "POSTGRES_URL=jdbc:postgresql://localhost:5432/test_db",
    "POSTGRES_USER=postgres",
    "POSTGRES_PASSWORD=test_password",
    "NEO4J_URI=bolt://localhost:7687",
    "NEO4J_USER=neo4j",
    "NEO4J_PASSWORD=test_password"
})
class LexIntelApplicationTests {

    @MockBean
    private ContractRepository contractRepository;

    @MockBean
    private DocumentAuditLogRepository documentAuditLogRepository;

    @MockBean
    private EntityManagerFactory entityManagerFactory;

    @Test
    void contextLoads() {
    }

}