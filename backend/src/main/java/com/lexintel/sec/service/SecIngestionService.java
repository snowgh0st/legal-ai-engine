package com.lexintel.sec.service;

import com.lexintel.sec.service.FilingGraphExtractionService;
import com.lexintel.sec.client.SecEdgarClient;
import com.lexintel.slack.service.SlackNotificationService;
import org.springframework.stereotype.Service;

@Service
public class SecIngestionService {

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
        String filingText = secEdgarClient.fetchLatest10KFilingText(cik);
        String companyName = "CIK-" + cik;

        // 1. Build & Save Neo4j Graph
        graphExtractionService.processAndPersistFiling(cik, companyName, filingText);

        // 2. Dispatch Slack alert
        slackNotificationService.sendSecIngestionAlert(cik, companyName, filingText.length());

        return "Successfully ingested 10-K filing and populated Neo4j knowledge graph for CIK: " + cik;
    }
}