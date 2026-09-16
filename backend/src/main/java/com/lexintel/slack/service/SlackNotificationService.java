package com.lexintel.slack.service;

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
        this.restClient = RestClient.builder().build();
    }

    /**
     * Posts a rich Block Kit compliance alert to Slack.
     */
    public void sendSecIngestionAlert(String cik, String companyName, int charCount) {
        // ✅ CORRECT: Skip only if null, empty, or still set to the generic placeholder string
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR/WEBHOOK/URL")) {
            System.out.println("Slack Webhook URL not configured. Skipping notification.");
            return;
        }

        // Build Slack Block Kit Payload
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