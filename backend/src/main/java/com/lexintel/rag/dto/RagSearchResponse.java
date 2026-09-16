package com.lexintel.rag.dto;

public record RagSearchResponse(
    String question,
    String answer,
    String graphContextUsed
) {}