package com.lexintel.sec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Partial mapping of SEC EDGAR's {@code /submissions/CIK{cik}.json} response — only the fields {@link com.lexintel.sec.client.SecEdgarClient} needs to locate the latest 10-K. */
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