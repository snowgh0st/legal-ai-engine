package com.lexintel.rag.dto;

/** Non-streaming RAG answer: the question asked, the LLM's answer, and the Neo4j context it was given (for debugging). */
public record RagSearchResponse(
    String question,
    String answer,
    String graphContextUsed
) {}