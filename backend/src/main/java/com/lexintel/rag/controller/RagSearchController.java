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
     * Streaming SSE endpoint formatted for Vercel AI SDK (0:"chunk"\n)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRagChat(@RequestBody RagSearchRequest request) {
        String query = request.getResolvedQuestion();
        log.info("Received streaming RAG chat request: '{}'", query);

        if (query.isBlank()) {
            log.warn("Empty prompt received in RagSearchRequest payload.");
            return Flux.empty();
        }

        return ragSearchService.streamAnswerWithSecContext(query)
                .map(chunk -> {
                    try {
                        return "0:" + objectMapper.writeValueAsString(chunk) + "\n";
                    } catch (Exception e) {
                        return "0:\"\"\n";
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error streaming chat response for query: '{}'", query, e);
                    return Flux.just("0:\"Error processing request: " + e.getMessage() + "\"\n");
                });
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