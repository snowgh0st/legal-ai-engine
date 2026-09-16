package com.lexintel.sec.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.schema.GeneratedValue.UUIDGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID; // 👈 Import java.util.UUID

@Node("Filing")
public class Filing {

    @Id
    @GeneratedValue(UUIDGenerator.class)
    private UUID id; // 👈 Change String to UUID

    private String formType;
    private long textLength;

    @Relationship(type = "HAS_RISK_FACTOR", direction = Relationship.Direction.OUTGOING)
    private Set<RiskFactor> riskFactors = new HashSet<>();

    public Filing() {}

    public Filing(String formType, long textLength) {
        this.formType = formType;
        this.textLength = textLength;
    }

    public void addRiskFactor(RiskFactor riskFactor) {
        this.riskFactors.add(riskFactor);
    }

    public UUID getId() { return id; }
    public String getFormType() { return formType; }
    public long getTextLength() { return textLength; }
    public Set<RiskFactor> getRiskFactors() { return riskFactors; }
}