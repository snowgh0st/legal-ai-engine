package com.lexintel.contracts.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Node("Party")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Party {

    @Id
    @Builder.Default
    private String partyId = UUID.randomUUID().toString(); // Standard String UUID

    private String name; // e.g., "Apex Global Enterprises Inc."

    private String partyType; // e.g., "CLIENT", "VENDOR", "CONTRACTOR"

    private String jurisdiction; // e.g., "Delaware, USA"
}