package com.lexintel.sec.controller;

import com.lexintel.sec.service.SecIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Backend-only CUJ for pulling a company's latest 10-K filing from SEC EDGAR into the knowledge
 * graph. NOTE: as of this writing nothing in the frontend calls this endpoint — the UI's SEC
 * lookups go through the separate agentic tool-calling flow in
 * {@code frontend/src/app/api/chat/route.ts} (which talks to SEC EDGAR directly from Next.js,
 * bypassing this controller entirely). This endpoint exists for manual/scripted ingestion
 * (curl, Postman, ops tooling) — see {@link SecIngestionService#ingestLatest10KByCik}.
 */
@RestController
@RequestMapping("/api/v1/sec")
public class SecIngestionController {

    private final SecIngestionService secIngestionService;

    public SecIngestionController(SecIngestionService secIngestionService) {
        this.secIngestionService = secIngestionService;
    }

    /**
     * SAD PATHS: a missing CIK is a 400 before any work starts; an invalid/unknown CIK or a CIK
     * with no 10-K on file surfaces as {@link IllegalArgumentException} from
     * {@link com.lexintel.sec.client.SecEdgarClient} and is also mapped to 400 (client error,
     * not a server bug). Anything else (SEC API down, network failure, graph write failure)
     * is a 500 with the exception message echoed in {@code message} for troubleshooting.
     */
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