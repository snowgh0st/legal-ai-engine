package com.lexintel.contracts.repository;

import com.lexintel.contracts.domain.Clause;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Neo4j access for {@link Clause} nodes, including a manual multi-hop traversal query for context expansion (currently unused by {@link com.lexintel.rag.service.RagSearchService}, which runs its own inline Cypher instead). */
@Repository
public interface ClauseRepository extends Neo4jRepository<Clause, String> {

    List<Clause> findByClauseType(String clauseType);

    // Cypher query for multi-hop Graph traversal (Context Expansion for RagSearch)
    @Query("""
        MATCH (c:Contract)-[:HAS_CLAUSE]->(cl:Clause)
        WHERE cl.clauseType = $type
        OPTIONAL MATCH (cl)-[r:DEPENDS_ON|CONFLICTS_WITH]->(related:Clause)
        RETURN cl, collect(r), collect(related)
        """)
    List<Clause> findClauseWithRelatedGraph(String type);
}