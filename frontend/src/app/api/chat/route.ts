import { createOpenAI } from '@ai-sdk/openai';
import {
  streamText,
  createUIMessageStreamResponse,
  toUIMessageStream,
  convertToModelMessages,
  type UIMessage,
} from 'ai';

const openai = createOpenAI({
  apiKey: process.env.OPENAI_API_KEY,
});

export async function POST(req: Request) {
  console.log('Active OpenAI Key Prefix:', process.env.OPENAI_API_KEY?.slice(0, 12));
  const { messages }: { messages: UIMessage[] } = await req.json();

  // Extract content from the latest user message
  const lastUserMessage = messages[messages.length - 1];
  let userQuery = '';

  if (lastUserMessage?.parts) {
    const textPart = lastUserMessage.parts.find((p) => p.type === 'text');
    if (textPart && 'text' in textPart) {
      userQuery = textPart.text;
    }
  }

  // Fetch contextual GraphRAG chunks from local Spring Boot backend
  let graphContext = '';
  try {
    const backendUrl = process.env.SPRING_BACKEND_URL || 'http://localhost:8080';
    const ragResponse = await fetch(`${backendUrl}/api/rag/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: userQuery }),
    });

    if (ragResponse.ok) {
      const data = await ragResponse.json();
      graphContext = data.context || '';
    }
  } catch (err) {
    console.warn('Spring Boot backend unavailable, proceeding with standard LLM stream:', err);
  }

  // Generate stream via OpenAI
  const result = streamText({
    model: openai('gpt-4o'),
    system: `You are an expert legal AI compliance assistant. Use the following legal graph database context to answer user questions when relevant:\n${graphContext}`,
    messages: await convertToModelMessages(messages),
  });

  // Convert and stream UI messages to client
  return createUIMessageStreamResponse({
    stream: toUIMessageStream({ stream: result.stream }),
  });
}