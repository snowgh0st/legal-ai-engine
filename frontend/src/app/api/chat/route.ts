import { createOpenAI } from '@ai-sdk/openai';
import { auth } from '@clerk/nextjs/server';
import {
  streamText,
  createUIMessageStreamResponse,
  toUIMessageStream,
  convertToModelMessages,
  type UIMessage,
  tool,
  stepCountIs,
} from 'ai';
import { z } from 'zod';

const openai = createOpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

export async function POST(req: Request) {
  const { messages }: { messages: UIMessage[] } = await req.json();

  // Extract the latest user query from UIMessage parts
  const lastUserMessage = messages[messages.length - 1];
  let userQuery = '';

  if (lastUserMessage?.parts) {
    const textPart = lastUserMessage.parts.find((p) => p.type === 'text');
    if (textPart && 'text' in textPart) {
      userQuery = textPart.text;
    }
  }

  // 1. Fetch deterministic Neo4j graph context from Spring Boot
  let graphContext = '';
  try {
    const backendUrl =
      process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_API_BASE_URL;

    if (backendUrl) {
      const cleanBackendUrl = backendUrl.replace(/\/$/, '');

      // Extract Clerk JWT session token on server-side
      const { getToken } = await auth();
      const token = await getToken();

      const ragResponse = await fetch(`${cleanBackendUrl}/api/v1/rag/context`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token && { Authorization: `Bearer ${token}` }),
        },
        body: JSON.stringify({ question: userQuery }),
      });

      if (ragResponse.ok) {
        graphContext = await ragResponse.text();
      } else {
        console.warn(
          `Spring Boot RAG context returned status ${ragResponse.status}`
        );
      }
    }
  } catch (err) {
    console.warn('Spring Boot backend unavailable for graph context:', err);
  }

  // 2. Execute Vercel AI SDK Agent with Embedded SEC EDGAR MCP Tools
  const result = streamText({
    model: openai('gpt-4o'),
    system: `You are LexIntel, an enterprise legal compliance orchestrator agent. You analyze legal contracts, cross-reference SEC regulatory filings, and flag compliance discrepancies. Use the provided Neo4j Graph Database Context when answering:
    ---
    ${graphContext || 'No relevant internal graph context found.'}
    ---
    If a user asks about a company's SEC filings, regulatory disclosures, or 10-K risks, use the embedded SEC EDGAR tools to fetch live data directly from SEC.gov.`,

    messages: await convertToModelMessages(messages),

    // Enable multi-step tool calls so the Agent can lookup CIK -> Fetch Filings -> Synthesize Answer automatically
    stopWhen: stepCountIs(5),

    tools: {
      /**
       * Tool 1: SEC EDGAR Ticker & CIK Resolver
       * Searches SEC EDGAR's company mapping ticker list to resolve names or tickers into 10-digit CIKs.
       */
      secCompanyLookup: tool({
        description:
          'Lookup official SEC CIK number and company entity details by ticker or company name.',
        inputSchema: z.object({
          identifier: z
            .string()
            .describe('Stock ticker (e.g., AAPL, MSFT) or company name'),
        }),
        execute: async ({ identifier }) => {
          try {
            // Fetch official SEC ticker-to-CIK mapping JSON
            const secRes = await fetch(
              'https://www.sec.gov/files/company_tickers.json',
              {
                headers: {
                  'User-Agent': 'LexIntel Compliance Engine legal@lexintel.com', // SEC mandate: AppName email
                  'Accept-Encoding': 'gzip, deflate',
                },
              }
            );

            if (!secRes.ok) {
              return { error: `SEC API returned status ${secRes.status}` };
            }

            const companyData = await secRes.json();
            const cleanIdentifier = identifier.trim().toUpperCase();

            // Search through SEC company map
            const matchedKey = Object.keys(companyData).find((key) => {
              const comp = companyData[key];
              return (
                comp.ticker.toUpperCase() === cleanIdentifier ||
                comp.title.toUpperCase().includes(cleanIdentifier)
              );
            });

            if (!matchedKey) {
              return {
                found: false,
                message: `No SEC entity found matching identifier '${identifier}'`,
              };
            }

            const match = companyData[matchedKey];
            const paddedCik = String(match.cik_str).padStart(10, '0');

            return {
              found: true,
              cik: paddedCik,
              ticker: match.ticker,
              companyName: match.title,
            };
          } catch (error) {
            return {
              error: 'Failed to communicate with SEC EDGAR API',
              details: String(error),
            };
          }
        },
      }),

      /**
       * Tool 2: SEC EDGAR Recent Filings Fetcher
       * Retrieves recent 10-K, 10-Q, or 8-K submission metadata for a given CIK.
       */
      getRecentSecFilings: tool({
        description:
          'Fetch recent SEC submission filings (10-K, 10-Q, 8-K) for a specific company CIK number.',
        inputSchema: z.object({
          cik: z
            .string()
            .describe('10-digit padded CIK number (e.g. 0000320193)'),
          formType: z
            .enum(['10-K', '10-Q', '8-K', 'ALL'])
            .optional()
            .default('10-K'),
        }),
        execute: async ({ cik, formType }) => {
          try {
            const formattedCik = cik.padStart(10, '0');
            const secSubmissionsUrl = `https://data.sec.gov/submissions/CIK${formattedCik}.json`;

            const res = await fetch(secSubmissionsUrl, {
              headers: {
                'User-Agent': 'LexIntel Compliance Engine legal@lexintel.com',
                'Accept-Encoding': 'gzip, deflate',
              },
            });

            if (!res.ok) {
              return {
                error: `SEC Submissions API returned status ${res.status}`,
              };
            }

            const data = await res.json();
            const recentFilings = data.filings?.recent;

            if (!recentFilings || !recentFilings.form) {
              return {
                found: false,
                message: 'No recent submission history found.',
              };
            }

            // Extract matching forms
            const results = [];
            for (
              let i = 0;
              i < recentFilings.form.length && results.length < 5;
              i++
            ) {
              const form = recentFilings.form[i];
              if (formType === 'ALL' || form === formType) {
                results.push({
                  accessionNumber: recentFilings.accessionNumber[i],
                  filingDate: recentFilings.filingDate[i],
                  reportDate: recentFilings.reportDate[i],
                  form: form,
                  primaryDocument: recentFilings.primaryDocument[i],
                });
              }
            }

            return {
              companyName: data.name,
              cik: formattedCik,
              sic: data.sic,
              sicDescription: data.sicDescription,
              filings: results,
            };
          } catch (error) {
            return {
              error: 'Failed to fetch SEC submissions',
              details: String(error),
            };
          }
        },
      }),
    },
  });

  return createUIMessageStreamResponse({
    stream: toUIMessageStream({ stream: result.stream }),
  });
}