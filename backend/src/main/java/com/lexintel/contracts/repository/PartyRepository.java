package com.lexintel.contracts.repository;

import com.lexintel.contracts.domain.Party;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

/** Plain CRUD Neo4j access for {@link Party} nodes — no custom queries needed yet. */
@Repository
public interface PartyRepository extends Neo4jRepository<Party, String> {
}