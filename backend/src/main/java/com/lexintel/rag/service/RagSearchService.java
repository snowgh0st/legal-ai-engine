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

/**
 * HOT PATH: core RAG (Retrieval-Augmented Generation) logic for the legal chat assistant.
 *
 * Flow for every chat message:
 *   1. Pull relevant facts out of the Neo4j contract knowledge graph ({@link #retrieveContext}).
 *   2. Hand that context + the user's question to the LLM via Spring AI's {@link ChatClient}.
 *   3. Stream (or return) the LLM's answer back to the controller.
 *
 * If you're troubleshooting "the assistant gave a weird/empty answer", start by checking
 * whether {@link #retrieveContext} actually matched anything in the graph — a bad match here
 * (wrong search term, no clauses in the DB) is the most common root cause.
 */
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
     * HOT PATH: builds the streaming response for {@code POST /api/v1/rag/chat}.
     *
     * The Neo4j lookup ({@link #retrieveContext}) runs on a bounded-elastic thread so it never
     * blocks the reactive pipeline, and is bounded by a strict 5-second timeout — if the graph
     * DB is slow or unreachable, the chat still responds (just without internal contract
     * context) instead of hanging the whole conversation. This timeout/error handling is the
     * most important SAD PATH in the chat flow: both branches below fall back to a plain string
     * that gets fed to the LLM as "context" so the user still gets an answer.
     */
    public Flux<String> streamAnswerWithSecContext(String question) {
        return Mono.fromCallable(() -> retrieveContext(question))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(TimeoutException.class, e -> {
                    // SAD PATH: Neo4j didn't respond in time — degrade gracefully rather than
                    // failing the whole chat request.
                    log.warn("Neo4j context retrieval timed out for query: '{}'. Proceeding with fallback context.", question);
                    return Mono.just("Graph context lookup timed out. Proceeding with standard RAG knowledge.");
                })
                .onErrorResume(e -> {
                    // SAD PATH: any other failure (driver error, query error) — same graceful
                    // degradation.
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
     * Traverses Neo4j and returns a raw graph sub-tree context string for the given question.
     *
     * This is a simple keyword-match lookup, not true vector/semantic search: the question is
     * reduced to a crude search term ({@link #extractSearchTerm}) and matched via
     * case-insensitive {@code CONTAINS} against clause text, clause type, and contract title.
     * If nothing matches, it falls back to the 5 most recent contract clauses
     * ({@link #retrieveFallbackContext}) rather than returning nothing, so the LLM always has
     * *some* grounding — a common EXECUTION PATH gotcha: a "no results" answer from the
     * assistant is more often a search-term miss here than an empty database.
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
            // SAD PATH: Neo4j session/query failure — surface a context string instead of
            // throwing, so the caller's timeout/error handling doesn't have to special-case this.
            log.error("Failed to query Neo4j graph context", e);
            return "No matching graph context found due to query error.";
        }

        if (graphFacts.isEmpty()) {
            // SAD PATH: zero keyword matches — fall back to a generic sample of clauses rather
            // than returning an empty context string.
            log.warn("No graph nodes matched term: '{}'. Running fallback query.", searchTerm);
            return retrieveFallbackContext();
        }

        return String.join("\n", graphFacts);
    }

    /**
     * Non-streaming variant used by {@code /api/v1/rag/query} and
     * {@code /api/v1/legal/query}: same context retrieval as the streaming hot path, but
     * waits for the full LLM response (no SEC-tool awareness, no timeout safeguard on the
     * Neo4j call) and returns it with the graph context attached for debugging.
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

    /** Flattens one Cypher result row into a single human/LLM-readable line of context. */
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

    /**
     * Crude keyword extraction: strips common English/legal stopwords so the Cypher
     * {@code CONTAINS} match in {@link #retrieveContext} has a better chance of hitting
     * meaningful terms (e.g. "What is the termination clause?" -> "termination"). This is NOT
     * NLP-grade — multi-word or synonym queries will often miss, which is the main reason
     * {@link #retrieveFallbackContext} exists.
     */
    private String extractSearchTerm(String question) {
        if (question == null || question.isBlank()) return "";
        return question.replaceAll("(?i)\\b(what|is|are|the|in|for|and|or|of|clause|section)\\b", "").trim();
    }

    /** SAD PATH fallback: returns a generic sample of clauses when the keyword search matches nothing. */
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