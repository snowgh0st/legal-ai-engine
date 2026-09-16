package com.lexintel.sec.controller;

import com.lexintel.sec.service.SecIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sec")
@CrossOrigin(origins = "http://localhost:3000")
public class SecIngestionController {

    private final SecIngestionService secIngestionService;

    public SecIngestionController(SecIngestionService secIngestionService) {
        this.secIngestionService = secIngestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<SecIngestionResponse> ingest10K(@RequestBody SecIngestionRequest request) {
        if (request.cik() == null || request.cik().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new SecIngestionResponse(null, "FAILED", "CIK number is required."));
        }

        try {
            String resultSummary = secIngestionService.ingestLatest10KByCik(request.cik());
            return ResponseEntity.ok(new SecIngestionResponse(
                    request.cik(),
                    "SUCCESS",
                    resultSummary
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new SecIngestionResponse(request.cik(), "FAILED", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new SecIngestionResponse(request.cik(), "ERROR", "Failed to ingest SEC filing: " + e.getMessage()));
        }
    }

    // --- INLINE REQUEST / RESPONSE DTOs ---
    public record SecIngestionRequest(String cik) {}

    public record SecIngestionResponse(
            String cik,
            String status,
            String message
    ) {}
}