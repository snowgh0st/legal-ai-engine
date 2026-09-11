package com.lexintel.contracts.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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