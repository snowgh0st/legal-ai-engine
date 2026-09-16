package com.lexintel.rag.service;

import com.lexintel.rag.dto.RagSearchResponse;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final Driver neo4jDriver;
    private final ChatClient chatClient;

    public RagSearchService(Driver neo4jDriver, ChatClient.Builder chatClientBuilder) {
        this.neo4jDriver = neo4jDriver;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Dedicated method for Next.js Vercel AI SDK streaming endpoint.
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
            var result = session.run(cypherQuery, Map.of("searchTerm", searchTerm));
            graphFacts = result.list(this::formatGraphRecord);
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
     * Full Spring AI non-streaming endpoint handler (retained for backward compatibility).
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
        // Strips common stop words to improve basic Cypher matching
        return question.replaceAll("(?i)\\b(what|is|are|the|in|for|and|or|of|clause|section)\\b", "").trim();
    }

    private String retrieveFallbackContext() {
        // Fallback Cypher execution returning top 5 clauses if keyword search misses
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
        }
    }
}