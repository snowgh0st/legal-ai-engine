package com.lexintel.contracts.repository;

import com.lexintel.contracts.domain.Party;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartyRepository extends Neo4jRepository<Party, String> {
}