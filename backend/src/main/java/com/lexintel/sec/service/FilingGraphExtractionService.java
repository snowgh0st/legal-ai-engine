package com.lexintel.sec.service;

import com.lexintel.sec.domain.Company;
import com.lexintel.sec.domain.Filing;
import com.lexintel.sec.domain.RiskFactor;
import com.lexintel.sec.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FilingGraphExtractionService {

    private final CompanyRepository companyRepository;

    public FilingGraphExtractionService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional("neo4jTransactionManager") // 👈 Explicitly specify the Neo4j transaction manager
    public Company processAndPersistFiling(String cik, String companyName, String raw10kText) {
        // 1. Fetch existing company or create new node
        Company company = companyRepository.findByCik(cik)
                .orElseGet(() -> new Company(cik, companyName));

        // 2. Create filing metadata node
        Filing filingNode = new Filing("10-K", raw10kText.length());

        // 3. Extract key risk sections (Item 1A parsing logic)
        List<RiskFactor> extractedRisks = extractRiskFactors(raw10kText);
        extractedRisks.forEach(filingNode::addRiskFactor);

        // 4. Connect relationships: (Company)-[:FILED]->(Filing)-[:HAS_RISK_FACTOR]->(RiskFactor)
        company.addFiling(filingNode);

        // 5. Persist complete sub-graph to Neo4j
        return companyRepository.save(company);
    }

    private List<RiskFactor> extractRiskFactors(String text) {
        List<RiskFactor> risks = new ArrayList<>();

        // Match common SEC Item 1A bullet headers or key compliance terms
        Pattern pattern = Pattern.compile("(?i)(cybersecurity|supply chain|regulatory|liquidity|competition|market risk)[^.\\n]*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        int count = 0;
        while (matcher.find() && count < 5) {
            String riskTopic = matcher.group(1).toUpperCase();
            String snippet = matcher.group(0).trim();
            risks.add(new RiskFactor(riskTopic, snippet));
            count++;
        }

        if (risks.isEmpty()) {
            risks.add(new RiskFactor("GENERAL", "General operational and legal risk factors noted in SEC filing."));
        }

        return risks;
    }
}