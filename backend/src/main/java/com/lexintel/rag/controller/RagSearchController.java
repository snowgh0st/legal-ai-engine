package com.lexintel.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper; // <--- MISSING IMPORT
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
     * Streaming SSE endpoint formatted for the Vercel AI SDK UI Message Stream Protocol
     * (data: {"type": ...}\n\n events, terminated with data: [DONE]\n\n)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRagChat(@RequestBody RagSearchRequest request) {
        String query = request.getResolvedQuestion();
        log.info("Received streaming RAG chat request: '{}'", query);

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
                    log.error("Error streaming chat response for query: '{}'", query, e);
                    return Flux.just(
                            toJson(Map.of("type", "error", "errorText", "Error processing request: " + e.getMessage())),
                            "[DONE]"
                    );
                });
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"errorText\":\"Serialization error\"}";
        }
    }

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