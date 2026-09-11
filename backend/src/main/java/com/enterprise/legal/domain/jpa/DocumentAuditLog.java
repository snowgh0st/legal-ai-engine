package com.enterprise.legal.domain.jpa;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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