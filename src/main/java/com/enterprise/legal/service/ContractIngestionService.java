package com.enterprise.legal.service;

import com.enterprise.legal.domain.Clause;
import com.enterprise.legal.domain.Contract;
import com.enterprise.legal.domain.Party;
import com.enterprise.legal.dto.ContractExtractionResponse;
import com.enterprise.legal.repository.ContractRepository;

import org.apache.tika.Tika;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
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
    private final Tika tika = new Tika();

    public ContractIngestionService(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            ContractRepository contractRepository) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.contractRepository = contractRepository;
    }

    public Contract processAndPersistContract(MultipartFile file) throws Exception {
        // 1. Extract raw text from PDF using Apache Tika
        String rawPdfText;
        try (InputStream inputStream = file.getInputStream()) {
            rawPdfText = tika.parseToString(inputStream);
        }

        // 2. Call Spring AI ChatClient with Structured Output
        ContractExtractionResponse extracted = chatClient.prompt()
                .user(u -> u.text("""
                    You are an enterprise legal entity extraction system.
                    Analyze the following contract text and extract metadata, contracting parties, and key clauses.
                    
                    Contract Text:
                    {content}
                    """)
                    .param("content", rawPdfText))
                .call()
                .entity(ContractExtractionResponse.class);

        // 3. Map extracted DTOs to Neo4j Domain Entities
        List<Party> parties = extracted.parties().stream()
                .map(p -> Party.builder()
                        .name(p.name())
                        .partyType(p.partyType())
                        .jurisdiction(p.jurisdiction())
                        .build())
                .toList();

        List<Clause> clauses = new ArrayList<>();
        for (ContractExtractionResponse.ClauseDTO cDto : extracted.clauses()) {
            // Generate Vector Embedding for Clause Semantic Search
            List<Double> vectorEmbedding = embeddingModel.embed(cDto.rawText());

            Clause clauseNode = Clause.builder()
                    .clauseId(cDto.clauseId())
                    .clauseType(cDto.clauseType())
                    .rawText(cDto.rawText())
                    .summary(cDto.summary())
                    .embedding(vectorEmbedding)
                    .build();

            clauses.add(clauseNode);
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

        // 4. Save entire object graph to Neo4j
        return contractRepository.save(contract);
    }
}