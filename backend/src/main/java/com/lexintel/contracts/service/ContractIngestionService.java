package com.lexintel.contracts.service;

import com.lexintel.contracts.domain.Clause;
import com.lexintel.contracts.domain.Contract;
import com.lexintel.contracts.domain.Party;
import com.lexintel.audit.domain.DocumentAuditLog;
import com.lexintel.contracts.dto.ContractExtractionResponse;
import com.lexintel.contracts.dto.GraphDataResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CUJ: turns an uploaded contract PDF into a queryable knowledge graph entry.
 *
 * Pipeline (see {@link #processAndPersistContract}):
 *   PDF bytes -> Apache Tika text extraction -> LLM structured extraction (parties/clauses) ->
 *   per-clause embeddings -> resolve clause DEPENDS_ON edges -> Neo4j graph write -> Postgres
 *   audit log write -> Slack notification.
 *
 * Everything runs inside one transaction ({@code @Transactional("transactionManager")}, the
 * JPA/Postgres manager) except the Neo4j write, which uses its own driver session and is NOT
 * covered by that transaction — see {@link com.lexintel.config.DatabaseConfig} for why there
 * are two separate transaction managers. In practice this means a failure after the Neo4j save
 * (e.g. the audit log insert) can leave a contract in the graph with no matching audit row.
 */
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

        // First pass: create every Clause node so cross-references between clauses have
        // something to point at.
        List<Clause> clauses = new ArrayList<>();
        Map<String, Clause> clausesById = new LinkedHashMap<>();
        for (ContractExtractionResponse.ClauseDTO cDto : extracted.clauses()) {
            List<Double> vectorEmbedding = embeddingModel.embed(cDto.rawText());
            Clause clause = Clause.builder()
                    .clauseId(cDto.clauseId())
                    .clauseType(cDto.clauseType())
                    .rawText(cDto.rawText())
                    .summary(cDto.summary())
                    .embedding(vectorEmbedding)
                    .build();
            clauses.add(clause);
            clausesById.put(clause.getClauseId(), clause);
        }

        // Second pass: resolve the LLM's dependsOnClauseIds into actual DEPENDS_ON graph edges
        // now that every Clause node exists to link to. Unknown/missing IDs (the LLM hallucinated
        // a reference, or referenced a clause it didn't extract) are silently dropped rather than
        // failing ingestion.
        for (ContractExtractionResponse.ClauseDTO cDto : extracted.clauses()) {
            if (cDto.dependsOnClauseIds() == null || cDto.dependsOnClauseIds().isEmpty()) {
                continue;
            }
            Clause clause = clausesById.get(cDto.clauseId());
            List<Clause> dependentClauses = cDto.dependsOnClauseIds().stream()
                    .map(clausesById::get)
                    .filter(Objects::nonNull)
                    .toList();
            clause.setDependentClauses(dependentClauses);
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

    /**
     * Builds the node/edge graph for the "Knowledge Graph Canvas" tab in the dashboard, backing
     * {@code GET /api/v1/legal/contracts/{contractId}/graph}. Pulls the 2-hop sub-graph via
     * {@link ContractRepository#findContractWithFullGraph} (contract -> parties, contract ->
     * clauses -> dependent/conflicting clauses) and flattens it into
     * {@link GraphDataResponse}'s Cytoscape-friendly shape.
     *
     * SAD PATH: throws {@link IllegalArgumentException} for an unknown contract ID — mapped to
     * 404 by {@link com.lexintel.contracts.controller.ContractController}.
     */
    public GraphDataResponse buildGraphData(String contractId) {
        Contract contract = contractRepository.findContractWithFullGraph(contractId)
                .orElseThrow(() -> new IllegalArgumentException("No contract found for id: " + contractId));

        // LinkedHashMap de-dupes nodes that the Cypher traversal may return more than once
        // (e.g. a clause reachable via both HAS_CLAUSE and a DEPENDS_ON back-reference) while
        // keeping a stable render order.
        Map<String, GraphDataResponse.GraphNode> nodesById = new LinkedHashMap<>();
        List<GraphDataResponse.GraphEdge> edges = new ArrayList<>();

        nodesById.put(contract.getContractId(),
                new GraphDataResponse.GraphNode(contract.getContractId(), contract.getTitle(), "Contract"));

        if (contract.getParties() != null) {
            for (Party party : contract.getParties()) {
                nodesById.put(party.getPartyId(),
                        new GraphDataResponse.GraphNode(party.getPartyId(), party.getName(), "Party"));
                edges.add(new GraphDataResponse.GraphEdge(contract.getContractId(), party.getPartyId(), "PARTY_TO"));
            }
        }

        if (contract.getClauses() != null) {
            for (Clause clause : contract.getClauses()) {
                nodesById.put(clause.getClauseId(),
                        new GraphDataResponse.GraphNode(clause.getClauseId(), clause.getClauseType(), "Clause"));
                edges.add(new GraphDataResponse.GraphEdge(contract.getContractId(), clause.getClauseId(), "HAS_CLAUSE"));

                if (clause.getDependentClauses() != null) {
                    for (Clause dependent : clause.getDependentClauses()) {
                        edges.add(new GraphDataResponse.GraphEdge(clause.getClauseId(), dependent.getClauseId(), "DEPENDS_ON"));
                    }
                }
                if (clause.getConflictingClauses() != null) {
                    for (Clause conflicting : clause.getConflictingClauses()) {
                        edges.add(new GraphDataResponse.GraphEdge(clause.getClauseId(), conflicting.getClauseId(), "CONFLICTS_WITH"));
                    }
                }
            }
        }

        return new GraphDataResponse(List.copyOf(nodesById.values()), edges);
    }

    /** SAD PATH: LLM-extracted dates are free-text and not guaranteed to be ISO-8601 — any unparseable value is silently stored as null rather than failing the whole ingestion. */
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