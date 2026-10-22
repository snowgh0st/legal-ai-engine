package com.lexintel.audit.repository;

import com.lexintel.audit.domain.DocumentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA access for the compliance audit trail. {@code @RepositoryRestResource} auto-exposes this
 * as a Spring Data REST endpoint at {@code /document-audit-logs} in addition to its programmatic
 * use in {@link com.lexintel.contracts.service.ContractIngestionService} — useful for support
 * staff to pull up ingestion history directly without a custom endpoint.
 */
@Repository
@RepositoryRestResource(path = "document-audit-logs", collectionResourceRel = "documentAuditLogs")
public interface DocumentAuditLogRepository extends JpaRepository<DocumentAuditLog, Long> {
    List<DocumentAuditLog> findByContractId(String contractId);
}