package com.lexintel.sec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SecSubmissionResponse(
    String cik,
    String entityType,
    String name,
    Filings filings
) {
    public record Filings(Recent recent) {}

    public record Recent(
        @JsonProperty("accessionNumber") List<String> accessionNumber,
        @JsonProperty("filingDate") List<String> filingDate,
        @JsonProperty("reportDate") List<String> reportDate,
        @JsonProperty("form") List<String> form,
        @JsonProperty("primaryDocument") List<String> primaryDocument
    ) {}
}