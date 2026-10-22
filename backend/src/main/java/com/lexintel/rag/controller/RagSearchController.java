package com.lexintel.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexintel.rag.dto.RagSearchRequest;
import com.lexintel.rag.dto.RagSearchResponse;
import com.lexintel.rag.service.RagSearchService;
import com.lexintel.sec.service.SecIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * HOT PATH: entry point for every chat message the LexIntel assistant answers.
 * The frontend ({@code LegalChat.tsx}) talks to {@code /api/v1/rag/chat} directly through the
 * Next.js proxy ({@code proxy.ts}), so requests here are the most frequently executed code in
 * the app. See {@link RagSearchService} for the Neo4j context lookup + LLM call that does the
 * actual work; this controller is only responsible for shaping requests/responses.
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagSearchController {

    private static final Logger log = LoggerFactory.getLogger(RagSearchController.class);

    private final RagSearchService ragSearchService;
    private final SecIngestionService secIngestionService;
    private final ObjectMapper objectMapper;

    public RagSearchController(
            RagSearchService ragSearchService, 
            SecIngestionService secIngestionService, 
            ObjectMapper objectMapper) {
        this.ragSearchService = ragSearchService;
        this.secIngestionService = secIngestionService;
        this.objectMapper = objectMapper;
    }

    /**
     * HOT PATH entry point — called on every message sent from the LexIntel chat UI.
     * Streaming SSE endpoint formatted for the Vercel AI SDK UI Message Stream Protocol
     * (data: {"type": ...}\n\n events, terminated with data: [DONE]\n\n).
     *
     * The request body shape varies depending on caller (plain {@code question}, a raw
     * {@code prompt}/{@code text}, or a full {@code messages} array) — see
     * {@link RagSearchRequest#getResolvedQuestion()} for how that's normalized into one string.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRagChat(@RequestBody RagSearchRequest request) {
        String query = request.getResolvedQuestion();
        log.info("Received streaming RAG chat request: '{}'", query);

        // SAD PATH: nothing resolvable from the payload — bail out with an empty stream rather
        // than sending an empty prompt to the LLM.
        if (query.isBlank()) {
            log.warn("Empty prompt received in RagSearchRequest payload.");
            return Flux.empty();
        }

        String textPartId = "0";

        Flux<String> textDeltas = ragSearchService.streamAnswerWithSecContext(query)
                .map(chunk -> toJson(Map.of("type", "text-delta", "id", textPartId, "delta", chunk)));

        // Spring's text/event-stream writer already prefixes each emitted element with
        // "data:" and appends the blank-line terminator, so these must be bare JSON strings.
        return Flux.concat(
                        Mono.just(toJson(Map.of("type", "start"))),
                        Mono.just(toJson(Map.of("type", "text-start", "id", textPartId))),
                        textDeltas,
                        Mono.just(toJson(Map.of("type", "text-end", "id", textPartId))),
                        Mono.just(toJson(Map.of("type", "finish"))),
                        Mono.just("[DONE]")
                )
                .onErrorResume(e -> {
                    // SAD PATH: anything that escapes RagSearchService (LLM failure, serialization
                    // bug, etc.) is converted into a well-formed SSE error frame instead of a raw
                    // 500 or a silently dropped connection, so the frontend's useChat() still gets
                    // a parseable stream to show the user.
                    log.error("Error streaming chat response for query: '{}'", query, e);
                    return Flux.just(
                            toJson(Map.of("type", "error", "errorText", "Error processing request: " + e.getMessage())),
                            "[DONE]"
                    );
                });
    }

    /** Serializes a stream event to JSON; falls back to a generic error frame if that itself fails. */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"errorText\":\"Serialization error\"}";
        }
    }

    /**
     * Non-streaming equivalent of {@link #streamRagChat}: returns the full answer plus the raw
     * graph context that was used to produce it. Not used by the chat UI directly, but useful
     * for debugging/support (e.g. via curl or Postman) since the response exposes
     * {@code graphContextUsed} for inspection.
     */
    @PostMapping("/query")
    public ResponseEntity<RagSearchResponse> queryRagContext(@RequestBody RagSearchRequest request) {
        log.info("Received RAG search query: {}", request.question());

        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            RagSearchResponse response = ragSearchService.answerLegalQuestion(request.question());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving graph context for query: {}", request.question(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Returns just the raw Neo4j graph context (no LLM call) for a question. This is the
     * endpoint the Next.js {@code /api/chat} route calls to pull internal contract context
     * before handing the conversation to the Vercel AI SDK — see
     * {@code frontend/src/app/api/chat/route.ts}.
     */
    @PostMapping("/context")
    public ResponseEntity<String> extractRawContext(@RequestBody RagSearchRequest request) {
        log.info("Received raw graph context extraction request: {}", request.question());

        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String context = ragSearchService.retrieveContext(request.question());
            return ResponseEntity.ok(context);
        } catch (Exception e) {
            log.error("Error extracting raw context for query: {}", request.question(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}