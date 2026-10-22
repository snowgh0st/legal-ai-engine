package com.lexintel.contracts.dto;

import java.util.List;

/**
 * Cytoscape-friendly shape of a contract's knowledge sub-graph: flat node/edge lists keyed by
 * domain ID. Built by {@link com.lexintel.contracts.service.ContractIngestionService#buildGraphData}
 * and consumed directly by the frontend's {@code GraphCanvas.tsx}, whose node/edge types mirror
 * this record.
 */
public record GraphDataResponse(List<GraphNode> nodes, List<GraphEdge> edges) {

    /** {@code type} is one of "Contract", "Party", or "Clause" — matches GraphCanvas's styling rules. */
    public record GraphNode(String id, String label, String type) {}

    /** {@code label} is the Neo4j relationship type (e.g. "PARTY_TO", "HAS_CLAUSE", "DEPENDS_ON"). */
    public record GraphEdge(String source, String target, String label) {}
}
