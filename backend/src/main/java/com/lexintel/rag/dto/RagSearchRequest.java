package com.lexintel.rag.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RagSearchRequest(
    @JsonAlias({"message", "text", "prompt", "query"})
    String question
) {
    @Override
    public String question() {
        return question != null ? question.trim() : "";
    }
}