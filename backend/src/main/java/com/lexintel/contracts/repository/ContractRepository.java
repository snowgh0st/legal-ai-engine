package com.lexintel.contracts.repository;

import com.lexintel.contracts.domain.Contract;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends Neo4jRepository<Contract, String> {

    Optional<Contract> findByTitle(String title);

    // Custom Cypher query to retrieve a full Contract sub-graph with 2-hop depth
    @Query("""
        MATCH (c:Contract {contractId: $contractId})
        OPTIONAL MATCH (c)-[r1:PARTY_TO]->(p:Party)
        OPTIONAL MATCH (c)-[r2:HAS_CLAUSE]->(cl:Clause)
        OPTIONAL MATCH (cl)-[r3:DEPENDS_ON|CONFLICTS_WITH]->(otherCl:Clause)
        RETURN c, collect(r1), collect(p), collect(r2), collect(cl), collect(r3), collect(otherCl)
        """)
    Optional<Contract> findContractWithFullGraph(String contractId);

    // Find contracts involving a specific legal party
    @Query("MATCH (c:Contract)-[:PARTY_TO]->(p:Party {name: $partyName}) RETURN c")
    List<Contract> findContractsByParty(String partyName);
}