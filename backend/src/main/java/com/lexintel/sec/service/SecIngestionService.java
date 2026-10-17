package com.lexintel.sec.service;

import com.lexintel.sec.client.SecEdgarClient;
import com.lexintel.slack.service.SlackNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SecIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SecIngestionService.class);

    private final SecEdgarClient secEdgarClient;
    private final SlackNotificationService slackNotificationService;
    private final FilingGraphExtractionService graphExtractionService;

    public SecIngestionService(SecEdgarClient secEdgarClient,
                               SlackNotificationService slackNotificationService,
                               FilingGraphExtractionService graphExtractionService) {
        this.secEdgarClient = secEdgarClient;
        this.slackNotificationService = slackNotificationService;
        this.graphExtractionService = graphExtractionService;
    }

    public String ingestLatest10KByCik(String cik) {
        log.info("Starting SEC 10-K ingestion pipeline for CIK: {}", cik);

        String filingText = secEdgarClient.fetchLatest10KFilingText(cik);
        String companyName = "CIK-" + cik;

        // 1. Parse filing text into Neo4j nodes (Company, Filing, RiskFactor, FinancialMetric)
        graphExtractionService.processAndPersistFiling(cik, companyName, filingText);

        // 2. Dispatch real-time Slack notification
        try {
            slackNotificationService.sendSecIngestionAlert(cik, companyName, filingText.length());
        } catch (Exception e) {
            log.warn("Non-critical failure dispatching Slack alert for CIK {}: {}", cik, e.getMessage());
        }

        log.info("Completed Neo4j graph extraction and persistence for CIK: {}", cik);
        return "Successfully ingested 10-K filing and populated Neo4j knowledge graph for CIK: " + cik;
    }
}