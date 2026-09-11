package com.lexintel.contracts.dto;

import java.util.List;

public record ContractExtractionResponse(
    String contractId,
    String title,
    String contractType,
    String effectiveDate,
    String expirationDate,
    String governingLaw,
    List<PartyDTO> parties,
    List<ClauseDTO> clauses
) {
    public record PartyDTO(String name, String partyType, String jurisdiction) {}
    public record ClauseDTO(
        String clauseId, 
        String clauseType, 
        String rawText, 
        String summary, 
        List<String> dependsOnClauseIds
    ) {}
}