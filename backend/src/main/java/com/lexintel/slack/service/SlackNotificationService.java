package com.lexintel.slack.service;

import com.lexintel.contracts.domain.Contract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class SlackNotificationService {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackNotificationService(@Value("${slack.webhook.url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;

        // Set 3-second connection and read timeouts so Slack issues never freeze backend threads
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());

        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public void notifyContractIngested(Contract contract) {
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR/WEBHOOK/URL")) {
            System.out.println("Slack Webhook URL not configured. Skipping notification.");
            return;
        }

        if (contract == null) {
            System.err.println("Cannot send Slack notification: contract is null.");
            return;
        }

        String jsonPayload = """
                {
                  "blocks": [
                    {
                      "type": "header",
                      "text": { "type": "plain_text", "text": "✅ Contract Ingested Successfully" }
                    },
                    {
                      "type": "section",
                      "fields": [
                        { "type": "mrkdwn", "text": "*Title:*\\n%s" },
                        { "type": "mrkdwn", "text": "*ID:*\\n%s" },
                        { "type": "mrkdwn", "text": "*Type:*\\n%s" },
                        { "type": "mrkdwn", "text": "*Parties:*\\n%d" }
                      ]
                    }
                  ]
                }
                """.formatted(
                    contract.getTitle() != null ? contract.getTitle() : "N/A",
                    contract.getContractId() != null ? contract.getContractId() : "N/A",
                    contract.getContractType() != null ? contract.getContractType() : "N/A",
                    contract.getParties() != null ? contract.getParties().size() : 0
                );

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonPayload)
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("Slack notification successfully sent!");
        } catch (Exception e) {
            System.err.println("Failed to dispatch Slack notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Posts a rich Block Kit compliance alert to Slack.
     */
    public void sendSecIngestionAlert(String cik, String companyName, int charCount) {
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR/WEBHOOK/URL")) {
            System.out.println("Slack Webhook URL not configured. Skipping notification.");
            return;
        }

        Map<String, Object> payload = Map.of(
            "text", "SEC 10-K Ingestion Alert: " + companyName,
            "blocks", List.of(
                Map.of(
                    "type", "header",
                    "text", Map.of("type", "plain_text", "text", "📄 SEC 10-K Filing Ingested")
                ),
                Map.of(
                    "type", "section",
                    "fields", List.of(
                        Map.of("type", "mrkdwn", "text", "*Company:* " + companyName),
                        Map.of("type", "mrkdwn", "text", "*CIK:* `" + cik + "`"),
                        Map.of("type", "mrkdwn", "text", "*Status:* `PROCESSED`"),
                        Map.of("type", "mrkdwn", "text", "*Characters:* " + charCount)
                    )
                ),
                Map.of(
                    "type", "context",
                    "elements", List.of(
                        Map.of("type", "mrkdwn", "text", "LexIntel RagSearch Pipeline • " + java.time.Instant.now())
                    )
                )
            )
        );

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("Slack notification successfully sent!");
        } catch (Exception e) {
            System.err.println("Failed to dispatch Slack notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}