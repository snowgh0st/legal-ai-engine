package com.lexintel.rag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Catch-all request shape for the RAG endpoints. Deliberately permissive ({@code question},
 * {@code prompt}, {@code text}, or a full {@code messages} array are all accepted) because this
 * same payload type is used both by direct API callers (simple {@code question} field) and by
 * the Vercel AI SDK chat transport (which sends a {@code messages} array of UI messages). See
 * {@link #getResolvedQuestion()} for how the two shapes are reconciled into one string.
 */
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
        @JsonProperty("text") String text,
        @JsonProperty("parts") List<MessagePart> parts
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessagePart(
        @JsonProperty("type") String type,
        @JsonProperty("text") String text
    ) {}

    /**
     * Extracts the prompt from direct string fields, message arrays, 
     * or nested parts structures sent by Vercel AI SDK.
     */
    public String getResolvedQuestion() {
        // 1. Direct top-level fields
        if (question != null && !question.isBlank()) return question.trim();
        if (prompt != null && !prompt.isBlank()) return prompt.trim();
        if (text != null && !text.isBlank()) return text.trim();

        // 2. Traversal of messages array (searching backward for last user message)
        if (messages != null && !messages.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);
                if ("user".equalsIgnoreCase(msg.role())) {
                    // Check parts array
                    if (msg.parts() != null && !msg.parts().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (MessagePart part : msg.parts()) {
                            if ("text".equalsIgnoreCase(part.type()) && part.text() != null && !part.text().isBlank()) {
                                sb.append(part.text()).append(" ");
                            }
                        }
                        if (!sb.toString().isBlank()) {
                            return sb.toString().trim();
                        }
                    }

                    // Check flat string fields
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