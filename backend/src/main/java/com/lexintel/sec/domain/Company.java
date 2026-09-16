package com.lexintel.sec.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Company")
public class Company {

    @Id
    private String cik; // Business key as String ID
    
    private String companyName;

    @Relationship(type = "FILED", direction = Relationship.Direction.OUTGOING)
    private Set<Filing> filings = new HashSet<>();

    public Company() {}

    public Company(String cik, String companyName) {
        this.cik = cik;
        this.companyName = companyName;
    }

    public void addFiling(Filing filing) {
        this.filings.add(filing);
    }

    // Getters and Setters
    public String getCik() { return cik; }
    public String getCompanyName() { return companyName; }
    public Set<Filing> getFilings() { return filings; }
}