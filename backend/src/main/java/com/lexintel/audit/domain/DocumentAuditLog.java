package com.lexintel.audit.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Postgres (JPA) compliance record of a contract ingestion event — separate from the Neo4j
 * graph itself, so audit history survives even if the graph node is later changed or deleted.
 * Written once per ingestion in {@link com.lexintel.contracts.service.ContractIngestionService};
 * {@code status} is currently always set to {@code "INGESTED"} on write (the {@code FAILED}/
 * {@code ANALYZED} values suggested by the field comment below aren't produced anywhere yet).
 */
@Entity
@Table(name = "document_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String contractId;

    @Column(nullable = false)
    private String fileName;

    private Long fileSizeBytes;

    @Column(nullable = false)
    private String uploadedBy;

    private String status; // INGESTED, FAILED, ANALYZED

    private Integer clauseCount;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}