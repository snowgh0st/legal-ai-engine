package com.lexintel.rag.controller;

import com.lexintel.rag.dto.RagSearchRequest;
import com.lexintel.rag.dto.RagSearchResponse;
import com.lexintel.rag.service.RagSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@CrossOrigin(origins = "http://localhost:3000")
public class RagSearchController {

    private static final Logger log = LoggerFactory.getLogger(RagSearchController.class);

    private final RagSearchService ragSearchService;

    public RagSearchController(RagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
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