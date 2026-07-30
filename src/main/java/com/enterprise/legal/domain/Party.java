package com.enterprise.legal.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Node("Party")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Party {

    @Id
    private String name; // e.g., "Acme Corp", "Vendor LLC"

    private String partyType; // e.g., "ORGANIZATION", "INDIVIDUAL"
    
    private String jurisdiction; // e.g., "Delaware, USA"
}