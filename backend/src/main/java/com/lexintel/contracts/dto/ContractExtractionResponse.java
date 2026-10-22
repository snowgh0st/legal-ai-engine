package com.lexintel.contracts.dto;

import java.util.List;

/**
 * Shape the LLM is asked to return during ingestion ({@link
 * com.lexintel.contracts.service.ContractIngestionService#processAndPersistContract}). Spring AI
 * binds the model's structured output directly to this record, so field names/types here
 * effectively define the extraction prompt's contract — renaming a field changes what the
 * LLM is asked to produce.
 */
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
        // Resolved into Clause.dependentClauses (DEPENDS_ON edges) by
        // ContractIngestionService#processAndPersistContract once every clause in this list has
        // been turned into a node. Only recognizes IDs within the same contract's clause list —
        // an ID the LLM hallucinated, or one belonging to a different contract, is dropped.
        List<String> dependsOnClauseIds
    ) {}
}