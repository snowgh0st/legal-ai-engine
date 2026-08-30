package com.enterprise.legal.service;

import com.enterprise.legal.domain.Clause;
import com.enterprise.legal.domain.Contract;
import com.enterprise.legal.domain.Party;
import com.enterprise.legal.domain.jpa.DocumentAuditLog;
import com.enterprise.legal.dto.ContractExtractionResponse;
import com.enterprise.legal.repository.ContractRepository;
import com.enterprise.legal.repository.jpa.DocumentAuditLogRepository;

import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContractIngestionService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ContractRepository contractRepository;
    private final DocumentAuditLogRepository auditLogRepository;
    private final Tika tika = new Tika();

    public ContractIngestionService(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            ContractRepository contractRepository,
            DocumentAuditLogRepository auditLogRepository) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.contractRepository = contractRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional("transactionManager")
    public Contract processAndPersistContract(MultipartFile file, String userId) throws Exception {
        // 1. Apache Tika Text Extraction
        String rawPdfText;
        try (InputStream inputStream = file.getInputStream()) {
            rawPdfText = tika.parseToString(inputStream);
        }

        // 2. Structured LLM Extraction via Spring AI
        ContractExtractionResponse extracted = chatClient.prompt()
                .user(u -> u.text("""
                    You are an enterprise legal entity extraction system.
                    Analyze the contract text and extract metadata, contracting parties, and key clauses.
                    
                    Contract Text:
                    {content}
                    """)
                    .param("content", rawPdfText))
                .call()
                .entity(ContractExtractionResponse.class);

        // 3. Map to Neo4j Entities
        List<Party> parties = extracted.parties().stream()
                .map(p -> Party.builder()
                        .name(p.name())
                        .partyType(p.partyType())
                        .jurisdiction(p.jurisdiction())
                        .build())
                .toList();

        List<Clause> clauses = new ArrayList<>();
        for (ContractExtractionResponse.ClauseDTO cDto : extracted.clauses()) {
            List<Double> vectorEmbedding = embeddingModel.embed(cDto.rawText());
            clauses.add(Clause.builder()
                    .clauseId(cDto.clauseId())
                    .clauseType(cDto.clauseType())
                    .rawText(cDto.rawText())
                    .summary(cDto.summary())
                    .embedding(vectorEmbedding)
                    .build());
        }

        Contract contract = Contract.builder()
                .contractId(extracted.contractId())
                .title(extracted.title())
                .contractType(extracted.contractType())
                .effectiveDate(extracted.effectiveDate() != null ? LocalDate.parse(extracted.effectiveDate()) : null)
                .expirationDate(extracted.expirationDate() != null ? LocalDate.parse(extracted.expirationDate()) : null)
                .governingLaw(extracted.governingLaw())
                .parties(parties)
                .clauses(clauses)
                .build();

        // 4. Save Graph to Neo4j
        Contract savedContract = contractRepository.save(contract);

        // 5. Save Structured Audit Trail to PostgreSQL
        DocumentAuditLog auditLog = DocumentAuditLog.builder()
                .contractId(savedContract.getContractId())
                .fileName(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .uploadedBy(userId != null ? userId : "SYSTEM_ANALYST")
                .status("INGESTED")
                .clauseCount(clauses.size())
                .build();
        auditLogRepository.save(auditLog);

        return savedContract;
    }
}