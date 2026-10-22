package com.lexintel.contracts.controller;

import com.lexintel.contracts.domain.Contract;
import com.lexintel.contracts.dto.GraphDataResponse;
import com.lexintel.rag.dto.RagSearchRequest;
import com.lexintel.rag.dto.RagSearchResponse;
import com.lexintel.contracts.service.ContractIngestionService;
import com.lexintel.rag.service.RagSearchService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * CUJ: contract upload/ingestion. This is the entry point the dashboard's "Ingest Contract PDF"
 * button hits ({@code frontend/src/app/page.tsx} -> {@code /api/v1/legal/contracts/ingest}).
 * All the real work (text extraction, LLM parsing, graph + audit persistence, Slack alert)
 * happens in {@link ContractIngestionService}; this controller only validates the request and
 * maps exceptions to HTTP status codes.
 */
@RestController
@RequestMapping("/api/v1/legal")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractIngestionService ingestionService;
    private final RagSearchService ragSearchService;

    public ContractController(
            ContractIngestionService ingestionService,
            RagSearchService ragSearchService) {
        this.ingestionService = ingestionService;
        this.ragSearchService = ragSearchService;
    }

    /**
     * CUJ entry point: upload a contract PDF for ingestion. Synchronous end-to-end —
     * the HTTP response doesn't come back until Tika extraction, the LLM structuring call,
     * both DB writes, and the Slack notification have all finished (see
     * {@link ContractIngestionService#processAndPersistContract}), so large/complex PDFs can
     * make this a slow request.
     *
     * SAD PATH: any failure anywhere in that pipeline (bad PDF, LLM error, DB write failure)
     * collapses to a generic 500 — check the stack trace in the logs ({@code ERROR PROCESSING
     * CONTRACT}) to see which stage actually failed.
     */
    @PostMapping(value = "/contracts/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Contract> ingestContract(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", defaultValue = "SYSTEM_ANALYST") String userId) {

        log.info(">>> INGESTION REQUEST RECEIVED: File = {}, Size = {} bytes, User = {}",
                file.getOriginalFilename(), file.getSize(), userId);

        try {
            Contract savedContract = ingestionService.processAndPersistContract(file, userId);
            return ResponseEntity.ok(savedContract);
        } catch (Exception e) {
            log.error(">>> ERROR PROCESSING CONTRACT: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Duplicate of {@code RagSearchController#queryRagContext} under the /legal namespace — same {@link RagSearchService} call, no extra blank-question guard. */
    @PostMapping("/query")
    public ResponseEntity<RagSearchResponse> queryKnowledgeGraph(@RequestBody RagSearchRequest request) {
        log.info(">>> QUERY REQUEST RECEIVED: {}", request.question());
        RagSearchResponse response = ragSearchService.answerLegalQuestion(request.question());
        return ResponseEntity.ok(response);
    }

    /**
     * Backs the dashboard's "Knowledge Graph Canvas" tab: returns the given contract's
     * sub-graph (parties, clauses, clause relationships) in the node/edge shape
     * {@code GraphCanvas.tsx} renders directly with Cytoscape. See
     * {@link ContractIngestionService#buildGraphData}.
     */
    @GetMapping("/contracts/{contractId}/graph")
    public ResponseEntity<GraphDataResponse> getContractGraph(@PathVariable String contractId) {
        try {
            return ResponseEntity.ok(ingestionService.buildGraphData(contractId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error(">>> ERROR BUILDING GRAPH FOR CONTRACT {}: ", contractId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}