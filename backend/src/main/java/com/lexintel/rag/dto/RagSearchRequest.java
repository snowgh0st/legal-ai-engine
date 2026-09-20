package com.lexintel.rag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RagSearchRequest(
    @JsonProperty("question") String question,
    @JsonProperty("prompt") String prompt,
    @JsonProperty("text") String text,
    @JsonProperty("messages") List<ChatMessage> messages
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("text") String text
    ) {}

    /**
     * Resolves the question prompt from direct fields or extracts 
     * the content of the last user message from Vercel AI SDK payload.
     */
    public String getResolvedQuestion() {
        if (question != null && !question.isBlank()) return question.trim();
        if (prompt != null && !prompt.isBlank()) return prompt.trim();
        if (text != null && !text.isBlank()) return text.trim();

        if (messages != null && !messages.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if ("user".equalsIgnoreCase(msg.role())) {
                    if (msg.content() != null && !msg.content().isBlank()) {
                        return msg.content().trim();
                    }
                    if (msg.text() != null && !msg.text().isBlank()) {
                        return msg.text().trim();
                    }
                }
            }
        }

        return "";
    }
}