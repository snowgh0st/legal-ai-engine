package com.lexintel.sec.client;

import com.lexintel.sec.dto.SecSubmissionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin HTTP client over the public SEC EDGAR APIs (no auth required, but EDGAR rejects
 * requests without a descriptive User-Agent — see the constructor). Two separate
 * {@link RestClient}s are used because EDGAR serves submission metadata from
 * {@code data.sec.gov} and the actual filing documents from {@code www.sec.gov/Archives} on
 * different hosts.
 *
 * Both clients are built from the Spring-managed {@code RestClient.Builder} (cloned once per
 * base URL via {@link org.springframework.web.client.RestClient.Builder#clone()}) so the
 * connection-pool and timeout tuning in {@link com.lexintel.config.RestClientConfig} actually
 * applies to EDGAR calls — a single 10-K ingestion makes at least two EDGAR requests
 * (submissions index + archive document), so reusing pooled connections matters here.
 */
@Component
public class SecEdgarClient {

    private static final Logger log = LoggerFactory.getLogger(SecEdgarClient.class);

    private final RestClient secDataClient;
    private final RestClient secArchivesClient;

    public SecEdgarClient(
            RestClient.Builder restClientBuilder,
            @Value("${sec.edgar.user-agent:LexIntel LegalAI/1.0 (compliance@lexintel.ai)}") String userAgent,
            @Value("${sec.edgar.base-url:https://data.sec.gov}") String baseUrl,
            @Value("${sec.edgar.archives-url:https://www.sec.gov/Archives}") String archivesUrl) {

        // SEC EDGAR strictly checks User-Agent header; requests without it are blocked (403 Forbidden)
        this.secDataClient = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();

        this.secArchivesClient = restClientBuilder.clone()
                .baseUrl(archivesUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    /**
     * Formats raw CIK into 10-digit zero-padded string required by SEC API.
     */
    private String formatCik(String rawCik) {
        // SAD PATH: missing/non-numeric CIK — fail fast with IllegalArgumentException so the
        // controller can map it to a 400 instead of letting a malformed request hit SEC's API.
        if (rawCik == null || rawCik.isBlank()) {
            throw new IllegalArgumentException("CIK cannot be null or empty.");
        }
        String digits = rawCik.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Invalid CIK format: " + rawCik);
        }
        return String.format("%010d", Long.parseLong(digits));
    }

    /**
     * Fetches recent submissions index for a given CIK.
     */
    public SecSubmissionResponse getSubmissions(String rawCik) {
        String paddedCik = formatCik(rawCik);
        log.info("Fetching SEC submission directory for CIK: {}", paddedCik);

        return secDataClient.get()
                .uri("/submissions/CIK{cik}.json", paddedCik)
                .retrieve()
                .body(SecSubmissionResponse.class);
    }

    /**
     * Fetches the raw text/HTML content of the most recent 10-K filing.
     *
     * SAD PATHS: a company with a malformed/empty submissions payload throws
     * {@link IllegalStateException} (treated as a server-side 500 by the controller); a company
     * that simply has no 10-K in its recent filings (e.g. a non-US issuer, or one that only
     * files 10-Qs) throws {@link IllegalArgumentException} (treated as a 400 — not our bug, bad
     * input). Note this only scans the "recent" filings window in the submissions index, so a
     * company whose 10-K has rolled off that window will also hit this branch.
     */
    public String fetchLatest10KFilingText(String rawCik) {
        String paddedCik = formatCik(rawCik);
        String numericCik = String.valueOf(Long.parseLong(paddedCik));

        SecSubmissionResponse response = getSubmissions(rawCik);
        if (response == null || response.filings() == null || response.filings().recent() == null) {
            throw new IllegalStateException("No filings payload returned for CIK: " + rawCik);
        }

        var recent = response.filings().recent();
        List<String> forms = recent.form();
        if (forms == null || forms.isEmpty()) {
            throw new IllegalStateException("Filing forms list is empty for CIK: " + rawCik);
        }

        // Locate index of the most recent 10-K form
        int index10K = -1;
        for (int i = 0; i < forms.size(); i++) {
            if ("10-K".equalsIgnoreCase(forms.get(i))) {
                index10K = i;
                break;
            }
        }

        if (index10K == -1) {
            throw new IllegalArgumentException("No recent 10-K filing found in SEC index for CIK: " + rawCik);
        }

        String accessionNumber = recent.accessionNumber().get(index10K);
        String primaryDoc = recent.primaryDocument().get(index10K);
        String rawAccession = accessionNumber.replace("-", "");

        // Build EDGAR archive URL path: /edgar/data/{numericCik}/{rawAccession}/{primaryDoc}
        String documentPath = String.format("/edgar/data/%s/%s/%s", numericCik, rawAccession, primaryDoc);
        log.info("Downloading primary 10-K document from SEC Archives: {}", documentPath);

        return secArchivesClient.get()
                .uri(documentPath)
                .retrieve()
                .body(String.class);
    }
}