"use client";

import React, { useEffect, useRef, useState } from "react";
import cytoscape, { Core } from "cytoscape";

interface GraphData {
  nodes: Array<{ id: string; label: string; type: "Contract" | "Party" | "Clause" }>;
  edges: Array<{ source: string; target: string; label: string }>;
}

interface GraphCanvasProps {
  /** Contract to fetch and render the real graph for. Omit to show the static demo graph. */
  contractId?: string | null;
}

const defaultGraphData: GraphData = {
  nodes: [
    { id: "c1", label: "MSA 2025-001", type: "Contract" as const },
    { id: "p1", label: "Apex Global", type: "Party" as const },
    { id: "p2", label: "Vertex AI", type: "Party" as const },
    { id: "cl1", label: "Confidentiality", type: "Clause" as const },
    { id: "cl2", label: "Termination", type: "Clause" as const },
  ],
  edges: [
    { source: "c1", target: "p1", label: "PARTY_TO" },
    { source: "c1", target: "p2", label: "PARTY_TO" },
    { source: "c1", target: "cl1", label: "HAS_CLAUSE" },
    { source: "c1", target: "cl2", label: "HAS_CLAUSE" },
    { source: "cl2", target: "cl1", label: "DEPENDS_ON" },
  ],
};

/**
 * Cytoscape visualization of the contract knowledge graph (Contract/Party/Clause nodes).
 *
 * When given a `contractId` (passed down from `page.tsx` after a successful ingestion), fetches
 * the real sub-graph from `GET /api/v1/legal/contracts/{contractId}/graph` (proxied to Spring
 * Boot's `ContractController#getContractGraph` by `proxy.ts`). With no `contractId` — e.g.
 * before anything has been ingested this session — it falls back to a hardcoded sample graph
 * purely so the tab isn't empty on first load.
 */
export default function GraphCanvas({ contractId }: GraphCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const cyRef = useRef<Core | null>(null);
  const [graphData, setGraphData] = useState<GraphData>(defaultGraphData);

  useEffect(() => {
    if (!contractId) {
      setGraphData(defaultGraphData);
      return;
    }

    let cancelled = false;

    fetch(`/api/v1/legal/contracts/${contractId}/graph`)
      .then((res) => {
        if (!res.ok) throw new Error(`Graph fetch failed with status ${res.status}`);
        return res.json();
      })
      .then((data: GraphData) => {
        if (!cancelled) setGraphData(data);
      })
      .catch((err) => {
        // SAD PATH: graph fetch failed (contract not found, network error) — keep showing the
        // demo graph rather than an empty canvas.
        console.warn("Failed to load contract graph, falling back to demo data:", err);
        if (!cancelled) setGraphData(defaultGraphData);
      });

    return () => {
      cancelled = true;
    };
  }, [contractId]);

  useEffect(() => {
    if (!containerRef.current) return;

    const elements = [
      ...graphData.nodes.map((node) => ({
        data: { id: node.id, label: node.label, type: node.type },
      })),
      ...graphData.edges.map((edge, idx) => ({
        data: { id: `e${idx}`, source: edge.source, target: edge.target, label: edge.label },
      })),
    ];

    cyRef.current = cytoscape({
      container: containerRef.current,
      elements: elements,
      style: [
        {
          selector: "node",
          style: {
            label: "data(label)",
            "color": "#f8fafc",
            "font-size": "11px",
            "text-valign": "bottom",
            "text-margin-y": 5,
            "background-color": "#6366f1",
            "width": 32,
            "height": 32,
            "border-width": 2,
            "border-color": "#818cf8",
          },
        },
        {
          selector: 'node[type = "Contract"]',
          style: { "background-color": "#4f46e5", "border-color": "#a5b4fc", "width": 40, "height": 40 },
        },
        {
          selector: 'node[type = "Party"]',
          style: { "background-color": "#10b981", "border-color": "#34d399" },
        },
        {
          selector: 'node[type = "Clause"]',
          style: { "background-color": "#f59e0b", "border-color": "#fbbf24" },
        },
        {
          selector: "edge",
          style: {
            label: "data(label)",
            "font-size": "9px",
            "color": "#94a3b8",
            "width": 1.5,
            "line-color": "#334155",
            "target-arrow-color": "#475569",
            "target-arrow-shape": "triangle",
            "curve-style": "bezier",
          },
        },
      ],
      layout: {
        name: "cose",
        animate: true,
        padding: 30,
      },
    });

    return () => {
      if (cyRef.current) cyRef.current.destroy();
    };
  }, [graphData]);

  return (
    <div className="w-full h-full relative bg-slate-950 rounded-xl overflow-hidden border border-slate-800">
      <div className="absolute top-3 left-3 z-10 flex gap-2">
        <span className="text-[10px] px-2 py-1 bg-indigo-950/80 text-indigo-300 border border-indigo-800 rounded font-mono">Contract</span>
        <span className="text-[10px] px-2 py-1 bg-emerald-950/80 text-emerald-300 border border-emerald-800 rounded font-mono">Party</span>
        <span className="text-[10px] px-2 py-1 bg-amber-950/80 text-amber-300 border border-amber-800 rounded font-mono">Clause</span>
      </div>
      <div ref={containerRef} className="w-full h-full" />
    </div>
  );
}
