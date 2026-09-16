package com.lexintel.sec.client;

import com.lexintel.sec.dto.SecSubmissionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class SecEdgarClient {

    private final RestClient secDataClient;
    private final RestClient secArchivesClient;

    public SecEdgarClient(
            @Value("${sec.edgar.user-agent}") String userAgent,
            @Value("${sec.edgar.base-url:https://data.sec.gov}") String baseUrl,
            @Value("${sec.edgar.archives-url:https://www.sec.gov/Archives}") String archivesUrl) {
        
        // SEC EDGAR strictly checks User-Agent header; requests without it are blocked (403 Forbidden)
        this.secDataClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();

        this.secArchivesClient = RestClient.builder()
                .baseUrl(archivesUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    /**
     * Formats raw CIK into 10-digit zero-padded string required by SEC API.
     */
    private String formatCik(String rawCik) {
        String digits = rawCik.replaceAll("\\D", "");
        return String.format("%010d", Long.parseLong(digits));
    }

    /**
     * Fetches recent submissions index for a given CIK.
     */
    public SecSubmissionResponse getSubmissions(String rawCik) {
        String paddedCik = formatCik(rawCik);
        return secDataClient.get()
                .uri("/submissions/CIK{cik}.json", paddedCik)
                .retrieve()
                .body(SecSubmissionResponse.class);
    }

    /**
     * Fetches the raw text/HTML content of the most recent 10-K filing.
     */
    public String fetchLatest10KFilingText(String rawCik) {
        String paddedCik = formatCik(rawCik);
        String numericCik = String.valueOf(Long.parseLong(paddedCik));

        SecSubmissionResponse response = getSubmissions(rawCik);
        if (response == null || response.filings() == null || response.filings().recent() == null) {
            throw new RuntimeException("No filings found for CIK: " + rawCik);
        }

        var recent = response.filings().recent();
        List<String> forms = recent.form();

        // Locate index of the most recent 10-K form
        int index10K = -1;
        for (int i = 0; i < forms.size(); i++) {
            if ("10-K".equalsIgnoreCase(forms.get(i))) {
                index10K = i;
                break;
            }
        }

        if (index10K == -1) {
            throw new IllegalArgumentException("No 10-K filing found for CIK: " + rawCik);
        }

        String accessionNumber = recent.accessionNumber().get(index10K);
        String primaryDoc = recent.primaryDocument().get(index10K);
        String rawAccession = accessionNumber.replace("-", "");

        // Build URL path: /edgar/data/{cik}/{rawAccession}/{primaryDoc}
        String documentPath = String.format("/edgar/data/%s/%s/%s", numericCik, rawAccession, primaryDoc);

        return secArchivesClient.get()
                .uri(documentPath)
                .retrieve()
                .body(String.class);
    }
}