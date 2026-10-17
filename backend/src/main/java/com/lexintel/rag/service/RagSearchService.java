package com.lexintel.rag.service;

import com.lexintel.rag.dto.RagSearchResponse;
import com.lexintel.sec.service.SecIngestionService;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final Driver neo4jDriver;
    private final ChatClient chatClient;
    private final SecIngestionService secIngestionService;

    public RagSearchService(
            Driver neo4jDriver, 
            ChatClient.Builder chatClientBuilder,
            SecIngestionService secIngestionService
    ) {
        this.neo4jDriver = neo4jDriver;
        this.chatClient = chatClientBuilder.build();
        this.secIngestionService = secIngestionService;
    }

    /**
     * Streams answers using combined Neo4j graph context and SEC EDGAR tool disclosures.
     * Integrates non-blocking context fetching with a strict 5-second timeout safeguard.
     */
    public Flux<String> streamAnswerWithSecContext(String question) {
        return Mono.fromCallable(() -> retrieveContext(question))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(TimeoutException.class, e -> {
                    log.warn("Neo4j context retrieval timed out for query: '{}'. Proceeding with fallback context.", question);
                    return Mono.just("Graph context lookup timed out. Proceeding with standard RAG knowledge.");
                })
                .onErrorResume(e -> {
                    log.error("Error retrieving graph context for query: '{}'", question, e);
                    return Mono.just("No graph context available due to an unexpected error.");
                })
                .flatMapMany(graphContext -> chatClient.prompt()
                        .system("""
                            You are LexIntel, an enterprise legal compliance and regulatory AI assistant.
                            Synthesize your analysis using both internal contract graph context and public SEC filing disclosures.
                            If the user asks about SEC filings, risk factors, or 10-K/10-Q documents, use your tools to query the SEC EDGAR service.
                            """)
                        .user(u -> u.text("""
                            Internal Contract Knowledge Graph Context:
                            {context}

                            User Compliance Query:
                            {question}
                            """)
                            .param("context", graphContext)
                            .param("question", question))
                        .stream()
                        .content());
    }

    /**
     * Traverses Neo4j and returns raw graph sub-tree context string.
     */
    public String retrieveContext(String question) {
        String searchTerm = extractSearchTerm(question);
        log.info("Executing Cypher sub-tree traversal for search term: '{}'", searchTerm);

        String cypherQuery = """
            MATCH (c:Contract)-[:HAS_CLAUSE]->(cl:Clause)
            OPTIONAL MATCH (c)-[:PARTY_TO]->(p:Party)
            OPTIONAL MATCH (cl)-[r:DEPENDS_ON|CONFLICTS_WITH]->(other:Clause)
            WHERE toLower(cl.rawText) CONTAINS toLower($searchTerm)
               OR toLower(cl.clauseType) CONTAINS toLower($searchTerm)
               OR toLower(c.title) CONTAINS toLower($searchTerm)
            RETURN c.title AS contractTitle,
                   cl.clauseType AS clauseType,
                   cl.rawText AS clauseText,
                   p.name AS partyName,
                   type(r) AS relationship,
                   other.clauseType AS relatedClauseType
            LIMIT 10
            """;

        List<String> graphFacts;
        try (var session = neo4jDriver.session()) {
            log.info("Opened Neo4j session, about to call session.run()");
            var result = session.run(cypherQuery, Map.of("searchTerm", searchTerm));
            log.info("session.run() returned, about to call result.list()");
            graphFacts = result.list(this::formatGraphRecord);
            log.info("result.list() returned {} records", graphFacts.size());
        } catch (Exception e) {
            log.error("Failed to query Neo4j graph context", e);
            return "No matching graph context found due to query error.";
        }

        if (graphFacts.isEmpty()) {
            log.warn("No graph nodes matched term: '{}'. Running fallback query.", searchTerm);
            return retrieveFallbackContext();
        }

        return String.join("\n", graphFacts);
    }

    /**
     * Full Spring AI non-streaming endpoint handler.
     */
    public RagSearchResponse answerLegalQuestion(String question) {
        String graphContext = retrieveContext(question);

        String answer = chatClient.prompt()
                .user(u -> u.text("""
                    You are a legal compliance AI engine.
                    Answer the question strictly based on the provided Graph Knowledge context below.
                    If the answer cannot be determined from the context, explicitly state so.
                    
                    Graph Knowledge Context:
                    {context}
                    
                    User Question:
                    {question}
                    """)
                    .param("context", graphContext)
                    .param("question", question))
                .call()
                .content();

        return new RagSearchResponse(question, answer, graphContext);
    }

    private String formatGraphRecord(Record record) {
        return String.format(
            "Contract: '%s' | Party: '%s' | Clause [%s]: '%s' | Rel: '%s' -> Related Clause [%s]",
            record.get("contractTitle").asString("N/A"),
            record.get("partyName").asString("N/A"),
            record.get("clauseType").asString("N/A"),
            record.get("clauseText").asString("N/A"),
            record.get("relationship").asString("NONE"),
            record.get("relatedClauseType").asString("N/A")
        );
    }

    private String extractSearchTerm(String question) {
        if (question == null || question.isBlank()) return "";
        return question.replaceAll("(?i)\\b(what|is|are|the|in|for|and|or|of|clause|section)\\b", "").trim();
    }

    private String retrieveFallbackContext() {
        String fallbackQuery = """
            MATCH (c:Contract)-[:HAS_CLAUSE]->(cl:Clause)
            RETURN c.title AS contractTitle, cl.clauseType AS clauseType, cl.rawText AS clauseText
            LIMIT 5
            """;
        try (var session = neo4jDriver.session()) {
            return String.join("\n", session.run(fallbackQuery).list(record -> 
                String.format("Contract: '%s' | Clause [%s]: '%s'",
                    record.get("contractTitle").asString("N/A"),
                    record.get("clauseType").asString("N/A"),
                    record.get("clauseText").asString("N/A")
                )
            ));
        } catch (Exception e) {
            log.error("Failed to execute Neo4j fallback query", e);
            return "No contract context available.";
        }
    }
}