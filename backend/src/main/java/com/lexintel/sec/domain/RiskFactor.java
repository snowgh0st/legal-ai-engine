package com.lexintel.sec.domain;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.GeneratedValue.UUIDGenerator;

import java.util.UUID; // 👈 Import java.util.UUID

/** A single risk disclosure snippet, keyword-matched out of filing text by {@link com.lexintel.sec.service.FilingGraphExtractionService}. */
@Node("RiskFactor")
public class RiskFactor {

    @Id
    @GeneratedValue(UUIDGenerator.class)
    private UUID id; // 👈 Change String to UUID

    private String topic;
    private String snippet;

    public RiskFactor() {}

    public RiskFactor(String topic, String snippet) {
        this.topic = topic;
        this.snippet = snippet;
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getSnippet() { return snippet; }
}