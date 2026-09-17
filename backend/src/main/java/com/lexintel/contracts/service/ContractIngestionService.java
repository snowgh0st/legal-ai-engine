package com.lexintel.contracts.service;

import com.lexintel.contracts.domain.Clause;
import com.lexintel.contracts.domain.Contract;
import com.lexintel.contracts.domain.Party;
import com.lexintel.audit.domain.DocumentAuditLog;
import com.lexintel.contracts.dto.ContractExtractionResponse;
import com.lexintel.contracts.repository.ContractRepository;
import com.lexintel.audit.repository.DocumentAuditLogRepository;
import com.lexintel.slack.service.SlackNotificationService;

import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContractIngestionService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ContractRepository contractRepository;
    private final DocumentAuditLogRepository auditLogRepository;
    private final Tika tika = new Tika();
    private final SlackNotificationService slackNotificationService;

    public ContractIngestionService(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            ContractRepository contractRepository,
            DocumentAuditLogRepository auditLogRepository,
            SlackNotificationService slackNotificationService) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.contractRepository = contractRepository;
        this.auditLogRepository = auditLogRepository;
        this.slackNotificationService = slackNotificationService;
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
                .effectiveDate(parseDateSafely(extracted.effectiveDate()))
                .expirationDate(parseDateSafely(extracted.expirationDate()))
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

        // 6. Notify Slack of Successful Ingestion
        slackNotificationService.notifyContractIngested(savedContract);

        return savedContract;
    }

    private LocalDate parseDateSafely(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}