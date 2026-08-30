"use client";

import React, { useState } from "react";
import DynamicGraphCanvas from "@/components/GraphCanvas";
import { 
  FileText, Network, Bot, Upload, Send, 
  Sparkles, Database, Layers, ArrowRight 
} from "lucide-react";

export default function LegalPortalDashboard() {
  const [activeTab, setActiveTab] = useState<"graph" | "chat">("chat");
  const [file, setFile] = useState<File | null>(null);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [messages, setMessages] = useState<Array<{ sender: "user" | "ai"; text: string; context?: string }>>([
    {
      sender: "ai",
      text: "Legal Knowledge Graph connected. Upload a contract PDF to extract entities into Neo4j and PostgreSQL, or query existing contracts.",
    },
  ]);
  const [inputQuery, setInputQuery] = useState("");

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      if (e.target.files && e.target.files[0]) {
        const selected = e.target.files[0];
        setFile(selected);
        // Revoke previous object URL if existing to avoid memory leaks
        if (fileUrl) URL.revokeObjectURL(fileUrl);
        setFileUrl(URL.createObjectURL(selected));
      }
    };

  const handleIngest = async () => {
    if (!file) return;
    setIsUploading(true);

    const formData = new FormData();
    formData.append("file", file);

    try {
      const res = await fetch("http://localhost:8080/api/v1/legal/contracts/ingest", {
        method: "POST",
        headers: {
          "X-User-Id": "ANALYST_FRONTEND",
        },
        body: formData,
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`Server returned status ${res.status}: ${errorText}`);
      }

      const data = await res.json();

      setMessages((prev) => [
        ...prev,
        {
          sender: "ai",
          text: `Contract "${data.title || file.name}" successfully extracted! Persisted to Neo4j graph with ${
            data.clauses ? data.clauses.length : 0
          } clauses and logged to PostgreSQL audit DB.`,
        },
      ]);
    } catch (err: any) {
      setMessages((prev) => [
        ...prev,
        { sender: "ai", text: `Ingestion Error: ${err.message}` },
      ]);
    } finally {
      setIsUploading(false);
    }
  };

  const handleQuery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputQuery.trim()) return;

    const userText = inputQuery;
    setInputQuery("");
    setMessages((prev) => [...prev, { sender: "user", text: userText }]);

    try {
      const res = await fetch("http://localhost:8080/api/v1/legal/query", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: userText }),
      });

      if (!res.ok) throw new Error("Query failed");
      const data = await res.json();

      setMessages((prev) => [
        ...prev,
        {
          sender: "ai",
          text: data.answer,
          context: data.graphContextUsed,
        },
      ]);
    } catch (err: any) {
      setMessages((prev) => [
        ...prev,
        { sender: "ai", text: `GraphRAG Error: ${err.message}` },
      ]);
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
                className="text-xs text-slate-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-slate-800 file:text-indigo-300 hover:file:bg-slate-700 cursor-pointer"
              />
              <button
                onClick={handleIngest}
                disabled={!file || isUploading}
                className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-xs font-medium rounded-md flex items-center gap-1 shrink-0"
              >
                {isUploading ? "Extracting..." : "Extract"} <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
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

        {/* Right Pane: Graph Viz / GraphRAG Chat (7 Cols) */}
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
                <Bot className="w-3.5 h-3.5" /> Compliance GraphRAG Chat
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
              <div className="flex-1 flex flex-col justify-between overflow-hidden">
                {/* Chat History */}
                <div className="flex-1 overflow-y-auto space-y-4 pr-2">
                  {messages.map((msg, i) => (
                    <div
                      key={i}
                      className={`flex gap-3 ${msg.sender === "user" ? "justify-end" : "justify-start"}`}
                    >
                      {msg.sender === "ai" && (
                        <div className="w-7 h-7 rounded bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shrink-0">
                          <Sparkles className="w-3.5 h-3.5" />
                        </div>
                      )}
                      <div
                        className={`max-w-xl text-xs p-3.5 rounded-xl leading-relaxed ${
                          msg.sender === "user"
                            ? "bg-indigo-600 text-white rounded-tr-none"
                            : "bg-slate-950 border border-slate-800 text-slate-300 rounded-tl-none"
                        }`}
                      >
                        <p>{msg.text}</p>
                        {msg.context && (
                          <details className="mt-2 pt-2 border-t border-slate-800">
                            <summary className="text-[10px] text-indigo-400 font-mono cursor-pointer">
                              Retrieved Graph Sub-tree Context
                            </summary>
                            <pre className="mt-1 p-2 bg-slate-900 rounded text-[9px] font-mono text-slate-400 overflow-x-auto">
                              {msg.context}
                            </pre>
                          </details>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                {/* Query Input */}
                <form onSubmit={handleQuery} className="mt-4 flex gap-2">
                  <input
                    type="text"
                    value={inputQuery}
                    onChange={(e) => setInputQuery(e.target.value)}
                    placeholder="Ask a compliance question..."
                    className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3 py-2.5 text-xs text-slate-100 focus:outline-none focus:border-indigo-500"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold rounded-lg flex items-center gap-1.5"
                  >
                    <span>Query</span>
                    <Send className="w-3.5 h-3.5" />
                  </button>
                </form>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}