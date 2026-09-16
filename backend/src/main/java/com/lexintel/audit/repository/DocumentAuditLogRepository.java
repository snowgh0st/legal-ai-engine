package com.lexintel.audit.repository;

import com.lexintel.audit.domain.DocumentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(path = "document-audit-logs", collectionResourceRel = "documentAuditLogs")
public interface DocumentAuditLogRepository extends JpaRepository<DocumentAuditLog, Long> {
    List<DocumentAuditLog> findByContractId(String contractId);
}