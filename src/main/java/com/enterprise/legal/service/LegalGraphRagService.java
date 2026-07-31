package com.enterprise.legal.service;

import com.enterprise.legal.dto.QueryDTOs.GraphRagResponse;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LegalGraphRagService {

    private final Driver neo4jDriver;
    private final ChatClient chatClient;

    public LegalGraphRagService(Driver neo4jDriver, ChatClient.Builder chatClientBuilder) {
        this.neo4jDriver = neo4jDriver;
        this.chatClient = chatClientBuilder.build();
    }

    public GraphRagResponse answerLegalQuestion(String question) {
        // 1. Cypher Graph Traversal: Traverse 2 hops deep to retrieve contract + party context
        String cypherQuery = """
            MATCH (c:Contract)-[:HAS_CLAUSE]->(cl:Clause)
            OPTIONAL MATCH (c)-[:PARTY_TO]->(p:Party)
            OPTIONAL MATCH (cl)-[r:DEPENDS_ON|CONFLICTS_WITH]->(other:Clause)
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
            var result = session.run(cypherQuery);
            graphFacts = result.list(record -> formatGraphRecord(record));
        }

        String graphContext = String.join("\n", graphFacts);

        // 2. Synthesize answer with LLM using full Graph context
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

        return new GraphRagResponse(question, answer, graphContext);
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
}