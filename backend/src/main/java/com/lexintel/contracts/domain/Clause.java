package com.lexintel.contracts.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A single clause extracted from a contract by the LLM during ingestion. {@code embedding} is
 * populated via {@link org.springframework.ai.embedding.EmbeddingModel} but isn't currently
 * queried by vector similarity anywhere — {@link com.lexintel.rag.service.RagSearchService}
 * does plain keyword matching on {@code rawText}/{@code clauseType} instead.
 *
 * {@code dependentClauses} (DEPENDS_ON) is populated during ingestion from the LLM's
 * {@code dependsOnClauseIds} — see
 * {@link com.lexintel.contracts.service.ContractIngestionService#processAndPersistContract}.
 * {@code conflictingClauses} (CONFLICTS_WITH) is still modeled here but nothing extracts or
 * populates it today — there's no equivalent "conflictsWithClauseIds" field on the extraction
 * DTO yet.
 */
@Node("Clause")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clause {

    @Id
    private String clauseId; // Unique ID (e.g., "CTR-2024-001_CLAUSE_03")

    private String clauseType; // e.g., "INDEMNIFICATION", "TERMINATION", "CONFIDENTIALITY"

    private String rawText; // Original verbatim legal text

    private String summary; // High-level AI summary of obligation

    private List<Double> embedding; // Vector representation for similarity search

    @Relationship(type = "DEPENDS_ON", direction = Relationship.Direction.OUTGOING)
    private List<Clause> dependentClauses;

    @Relationship(type = "CONFLICTS_WITH", direction = Relationship.Direction.OUTGOING)
    private List<Clause> conflictingClauses;
}