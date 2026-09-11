package com.lexintel.contracts.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Node("Contract")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

    @Id
    private String contractId; // e.g., "CTR-2024-001"

    private String title; // e.g., "Master Services Agreement"

    private String contractType; // e.g., "MSA", "NDA", "SOW"

    private LocalDate effectiveDate;

    private LocalDate expirationDate;

    private String governingLaw; // e.g., "State of New York"

    @Relationship(type = "PARTY_TO", direction = Relationship.Direction.OUTGOING)
    private List<Party> parties;

    @Relationship(type = "HAS_CLAUSE", direction = Relationship.Direction.OUTGOING)
    private List<Clause> clauses;
}