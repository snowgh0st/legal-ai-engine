package com.lexintel.sec.repository;

import com.lexintel.sec.domain.Company;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Neo4j access for {@link Company} nodes; {@code findByCik} is used by SEC ingestion to avoid creating duplicate Company nodes on re-ingestion. */
@Repository
public interface CompanyRepository extends Neo4jRepository<Company, String> {
    Optional<Company> findByCik(String cik);
}