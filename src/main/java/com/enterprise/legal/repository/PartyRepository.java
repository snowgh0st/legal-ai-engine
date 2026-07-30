package com.enterprise.legal.repository;

import com.enterprise.legal.domain.Party;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartyRepository extends Neo4jRepository<Party, String> {
}