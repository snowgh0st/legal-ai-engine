'use client';

import { useState } from 'react';
import { useChat } from '@ai-sdk/react';
import { DefaultChatTransport } from 'ai';
import { Bot, User, Send, Loader2, ShieldCheck, FileText } from 'lucide-react';
import { useAuth } from "@clerk/nextjs";

export default function LegalChat() {
  const [input, setInput] = useState('');
  const { getToken } = useAuth();

  const { messages, sendMessage, status } = useChat({
    transport: new DefaultChatTransport({
      api: '/api/v1/rag/chat',
      headers: async (): Promise<Record<string, string>> => {
        const token = await getToken({ skipCache: true });
        if (!token) return {};

        const authHeader = token.startsWith("Bearer ") ? token : `Bearer ${token}`;
        return {
          Authorization: authHeader,
        };
      },
    }),
  });

  const isLoading = status === 'submitted' || status === 'streaming';

  const handleSubmit = async (e: React.SyntheticEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage = input;
    setInput('');

    try {
      await sendMessage({ text: userMessage });
    } catch (error) {
      console.error("Failed to send chat message:", error);
    }
  };

  return (
    <div className="flex flex-col h-[650px] w-full max-w-4xl mx-auto bg-slate-900 border border-slate-800 rounded-xl shadow-2xl overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 bg-slate-950 border-b border-slate-800">
        <div className="flex items-center gap-3">
          <div className="p-2 bg-blue-600/20 text-blue-400 rounded-lg border border-blue-500/30">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-sm font-semibold text-slate-100">LexIntel Assistant</h2>
            <p className="text-xs text-slate-400">RagSearch & Compliance Stream Engine</p>
          </div>
        </div>
        <span className="flex items-center gap-1.5 text-xs text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20 font-medium">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          Active
        </span>
      </div>

      {/* Messages Stream Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
            <FileText className="w-12 h-12 mb-3 stroke-[1.5] text-slate-600" />
            <p className="text-sm font-medium text-slate-400">No active audit query</p>
            <p className="text-xs max-w-sm mt-1">
              Ask about compliance regulations, contract risk factors, or SEC disclosures.
            </p>
          </div>
        ) : (
          messages.map((message) => {
            const isUser = message.role === 'user';
            
            // Safely extract text across Vercel AI SDK versions
            const textContent =
              message.parts
                ?.filter((part) => part.type === 'text')
                .map((part) => part.text)
                .join('') ||
              (message as unknown as { content?: string }).content ||
              '';

            return (
              <div
                key={message.id}
                className={`flex gap-3 max-w-[85%] ${
                  isUser ? 'ml-auto flex-row-reverse' : 'mr-auto'
                }`}
              >
                <div
                  className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border ${
                    isUser
                      ? 'bg-blue-600 text-white border-blue-500'
                      : 'bg-slate-800 text-blue-400 border-slate-700'
                  }`}
                >
                  {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                </div>

                <div
                  className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${
                    isUser
                      ? 'bg-blue-600 text-white rounded-tr-none'
                      : 'bg-slate-800/80 text-slate-200 border border-slate-700/60 rounded-tl-none'
                  }`}
                >
                  <p className="whitespace-pre-wrap">{textContent}</p>
                </div>
              </div>
            );
          })
        )}

        {isLoading && (
          <div className="flex gap-3 mr-auto items-center text-xs text-slate-400 pl-11">
            <Loader2 className="w-4 h-4 animate-spin text-blue-400" />
            <span>Parsing legal graph context & streaming response...</span>
          </div>
        )}
      </div>

      {/* Input Form */}
      <form
        onSubmit={handleSubmit}
        className="p-4 bg-slate-950 border-t border-slate-800 flex gap-2"
      >
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about SEC risk factors, contract liabilities..."
          disabled={isLoading}
          className="flex-1 bg-slate-900 border border-slate-800 text-slate-100 placeholder-slate-500 text-sm rounded-lg px-4 py-2.5 focus:outline-none focus:border-blue-500 transition-colors disabled:opacity-50"
        />
        <button
          type="submit"
          disabled={isLoading || !input.trim()}
          className="bg-blue-600 hover:bg-blue-500 text-white px-4 py-2.5 rounded-lg text-sm font-medium flex items-center gap-2 transition-colors disabled:opacity-50 disabled:hover:bg-blue-600"
        >
          {isLoading ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <>
              <span>Send</span>
              <Send className="w-4 h-4" />
            </>
          )}
        </button>
      </form>
    </div>
  );
}