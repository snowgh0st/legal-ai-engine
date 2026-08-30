package com.enterprise.legal.controller;

import com.enterprise.legal.domain.Contract;
import com.enterprise.legal.dto.QueryDTOs.GraphRagRequest;
import com.enterprise.legal.dto.QueryDTOs.GraphRagResponse;
import com.enterprise.legal.service.ContractIngestionService;
import com.enterprise.legal.service.LegalGraphRagService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/legal")
public class LegalContractController {

    private final ContractIngestionService ingestionService;
    private final LegalGraphRagService graphRagService;

    public LegalContractController(
            ContractIngestionService ingestionService,
            LegalGraphRagService graphRagService) {
        this.ingestionService = ingestionService;
        this.graphRagService = graphRagService;
    }

    // PDF Contract Ingestion Endpoint
    @PostMapping(value = "/contracts/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Contract> ingestContract(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", defaultValue = "SYSTEM_ANALYST") String userId) {
        try {
            // Updated to pass both file and userId to ingestionService
            Contract savedContract = ingestionService.processAndPersistContract(file, userId);
            return ResponseEntity.ok(savedContract);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GraphRAG Query Endpoint
    @PostMapping("/query")
    public ResponseEntity<GraphRagResponse> queryKnowledgeGraph(@RequestBody GraphRagRequest request) {
        GraphRagResponse response = graphRagService.answerLegalQuestion(request.question());
        return ResponseEntity.ok(response);
    }
}