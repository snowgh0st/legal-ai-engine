"use client";

import React, { useState } from "react";
import { useAuth, UserButton } from "@clerk/nextjs";
import DynamicGraphCanvas from "@/components/GraphCanvas";
import LegalChat from "@/components/LegalChat";
import { 
  FileText, Network, Bot, Upload, 
  Database, Layers, ArrowRight 
} from "lucide-react";

export default function LegalPortalDashboard() {
  const { getToken } = useAuth();
  const [activeTab, setActiveTab] = useState<"graph" | "chat">("chat");
  const [file, setFile] = useState<File | null>(null);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [ingestStatus, setIngestStatus] = useState<string | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selected = e.target.files[0];
      setFile(selected);
      if (fileUrl) URL.revokeObjectURL(fileUrl);
      setFileUrl(URL.createObjectURL(selected));
      setIngestStatus(null);
    }
  };

  const handleIngest = async () => {
    if (!file) return;
    setIsUploading(true);
    setIngestStatus(null);

    const formData = new FormData();
    formData.append("file", file);

    try {
      // 1. Retrieve short-lived Clerk JWT
      const token = await getToken({ template: "spring-boot" });

      // 2. Pass Bearer token to Spring Boot Resource Server
      const res = await fetch("/api/v1/legal/contracts/ingest", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
        body: formData,
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`Server returned status ${res.status}: ${errorText}`);
      }

      const data = await res.json();
      setIngestStatus(
        `Extracted "${data.title || file.name}"! Persisted to Neo4j (${data.clauses ? data.clauses.length : 0} clauses) & PostgreSQL.`
      );
    } catch (err: any) {
      setIngestStatus(`Ingestion Error: ${err.message}`);
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="flex flex-col h-screen bg-slate-950 text-slate-100 font-sans">
      {/* Top Navigation */}
      <header className="h-16 border-b border-slate-800 bg-slate-900/90 px-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-indigo-600 rounded-lg shadow-lg shadow-indigo-600/30">
            <Layers className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-sm font-bold tracking-wide">LEGAL AI ENGINE</h1>
            <p className="text-[10px] text-indigo-400 font-mono">Next.js + Java 21 Spring AI + Neo4j</p>
          </div>
        </div>

        <div className="flex items-center gap-4 text-xs font-mono text-slate-400">
          <span className="flex items-center gap-1.5"><Database className="w-3.5 h-3.5 text-emerald-400" /> Neo4j Bolt</span>
          <span className="flex items-center gap-1.5"><Database className="w-3.5 h-3.5 text-blue-400" /> PostgreSQL JPA</span>
          
          {/* Clerk Profile Menu */}
          <div className="pl-2 border-l border-slate-800 flex items-center">
            <UserButton />
          </div>
        </div>
      </header>

      {/* Split Screen Workspace */}
      <div className="flex-1 grid grid-cols-12 gap-0 overflow-hidden">
        
        {/* Left Pane: PDF Viewer & Ingestion (5 Cols) */}
        <div className="col-span-5 border-r border-slate-800 bg-slate-900/50 p-4 flex flex-col gap-4">
          <div className="p-4 bg-slate-900 rounded-xl border border-slate-800 flex flex-col gap-3">
            <label className="text-xs font-semibold text-slate-300 flex items-center gap-2">
              <Upload className="w-4 h-4 text-indigo-400" /> Ingest Contract PDF
            </label>
            <div className="flex gap-2">
              <input
                type="file"
                accept=".pdf"
                onChange={handleFileChange}
                className="text-xs text-slate-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-indigo-300 hover:file:bg-slate-700 cursor-pointer flex-1"
              />
              <button
                onClick={handleIngest}
                disabled={!file || isUploading}
                className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-xs font-medium rounded-md flex items-center gap-1 shrink-0 transition-colors"
              >
                {isUploading ? "Extracting..." : "Extract"} <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
            {ingestStatus && (
              <p className="text-[11px] font-mono text-indigo-300 bg-indigo-950/50 border border-indigo-900/50 p-2 rounded">
                {ingestStatus}
              </p>
            )}
          </div>

          {/* Embedded PDF Previewer Pane */}
          <div className="flex-1 bg-slate-950 rounded-xl border border-slate-800 overflow-hidden flex flex-col items-center justify-center">
            {fileUrl ? (
              <iframe src={fileUrl} className="w-full h-full border-none" title="Contract PDF Preview" />
            ) : (
              <div className="text-center p-6 text-slate-500 flex flex-col items-center gap-2">
                <FileText className="w-10 h-10 stroke-1" />
                <p className="text-xs">No PDF loaded. Select a file above to view preview.</p>
              </div>
            )}
          </div>
        </div>

        {/* Right Pane: Graph Viz / RagSearch Chat (7 Cols) */}
        <div className="col-span-7 flex flex-col bg-slate-900/30">
          
          {/* Mode Switcher Tabs */}
          <div className="h-12 border-b border-slate-800 px-4 flex items-center justify-between bg-slate-900/60">
            <div className="flex gap-2">
              <button
                onClick={() => setActiveTab("chat")}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-2 transition-colors ${
                  activeTab === "chat" ? "bg-indigo-600 text-white" : "text-slate-400 hover:text-slate-200"
                }`}
              >
                <Bot className="w-3.5 h-3.5" /> Compliance RagSearch Chat
              </button>
              <button
                onClick={() => setActiveTab("graph")}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium flex items-center gap-2 transition-colors ${
                  activeTab === "graph" ? "bg-indigo-600 text-white" : "text-slate-400 hover:text-slate-200"
                }`}
              >
                <Network className="w-3.5 h-3.5" /> Knowledge Graph Canvas
              </button>
            </div>
          </div>

          {/* Tab Content */}
          <div className="flex-1 p-4 overflow-hidden flex flex-col">
            {activeTab === "graph" ? (
              <DynamicGraphCanvas />
            ) : (
              <div className="flex-1 h-full overflow-hidden">
                <LegalChat />
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}