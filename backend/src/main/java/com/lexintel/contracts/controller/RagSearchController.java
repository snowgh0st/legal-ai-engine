package com.lexintel.contracts.controller;

import com.lexintel.contracts.service.LegalGraphRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "http://localhost:3000")
public class RagSearchController {

    private static final Logger log = LoggerFactory.getLogger(RagSearchController.class);

    private final LegalGraphRagService legalGraphRagService;

    // Inject your existing LegalGraphRagService
    public RagSearchController(LegalGraphRagService legalGraphRagService) {
        this.legalGraphRagService = legalGraphRagService;
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchGraphContext(@RequestBody SearchRequest request) {
        log.info("Received GraphRAG search query: {}", request.query());

        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Call your existing service method (e.g., retrieveContext, searchGraph, etc.)
            String graphContext = legalGraphRagService.retrieveContext(request.query());

            return ResponseEntity.ok(new SearchResponse(
                request.query(),
                graphContext,
                List.of("Neo4j_Graph_Context")
            ));
        } catch (Exception e) {
            log.error("Error retrieving graph context for query: {}", request.query(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public record SearchRequest(String query) {}

    public record SearchResponse(
        String query,
        String graphContextUsed,
        List<String> sources
    ) {}
}