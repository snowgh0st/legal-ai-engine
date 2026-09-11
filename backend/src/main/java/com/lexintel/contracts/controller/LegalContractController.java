package com.lexintel.contracts.controller;

import com.lexintel.contracts.domain.Contract;
import com.lexintel.contracts.dto.QueryDTOs.GraphRagRequest;
import com.lexintel.contracts.dto.QueryDTOs.GraphRagResponse;
import com.lexintel.contracts.service.ContractIngestionService;
import com.lexintel.contracts.service.LegalGraphRagService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/legal")
public class LegalContractController {

    private static final Logger log = LoggerFactory.getLogger(LegalContractController.class);

    private final ContractIngestionService ingestionService;
    private final LegalGraphRagService graphRagService;

    public LegalContractController(
            ContractIngestionService ingestionService,
            LegalGraphRagService graphRagService) {
        this.ingestionService = ingestionService;
        this.graphRagService = graphRagService;
    }

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

    @PostMapping("/query")
    public ResponseEntity<GraphRagResponse> queryKnowledgeGraph(@RequestBody GraphRagRequest request) {
        log.info(">>> QUERY REQUEST RECEIVED: {}", request.question());
        GraphRagResponse response = graphRagService.answerLegalQuestion(request.question());
        return ResponseEntity.ok(response);
    }
}