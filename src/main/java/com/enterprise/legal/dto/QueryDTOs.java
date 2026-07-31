package com.enterprise.legal.dto;

public class QueryDTOs {
    public record GraphRagRequest(String question) {}
    public record GraphRagResponse(String question, String answer, String graphContextUsed) {}
}