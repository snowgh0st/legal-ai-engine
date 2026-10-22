# ⚖️ LexIntel — Legal Contract & Compliance Knowledge Graph

**An enterprise-grade GraphRAG engine that turns raw PDF contracts and SEC filings into a queryable Neo4j knowledge graph, powering legal and compliance intelligence with grounded, multi-hop retrieval.**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M1-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![Neo4j](https://img.shields.io/badge/Neo4j-5.18%20Enterprise-008CC1?logo=neo4j&logoColor=white)](https://neo4j.com/)
[![Apache Tika](https://img.shields.io/badge/Apache%20Tika-2.9.2-D22128?logo=apache&logoColor=white)](https://tika.apache.org/)
[![OpenAI](https://img.shields.io/badge/LLM-GPT--4o-412991?logo=openai&logoColor=white)](https://platform.openai.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Overview
LexIntel ingests multi-page commercial contracts (PDF) and SEC regulatory filings, uses LLM-powered structured extraction to map legal entities, clauses, and obligations, and persists them into a **Neo4j Knowledge Graph** backed by **Cloud SQL PostgreSQL** relational persistence.

Instead of flat, vector-only RAG, queries traverse graph edges (e.g., `Contract ─HAS_CLAUSE─▶ Clause ─DEPENDS_ON / CONFLICTS_WITH─▶ Clause`) alongside vector search to assemble contextual grounding before synthesizing answers with full explainability traces.


## Features

- ⚡ **Decoupled Modern Frontend** — Built with Next.js App Router, TypeScript, Tailwind CSS, Framer Motion, and the Vercel AI SDK, with Speed Insights and Web Analytics enabled.

- 🔐 **Edge Authentication & Middleware** — Secured using Clerk authentication (clerkMiddleware) with session propagation across server components and API route handlers.

- 📄 **Multi-Source Ingestion** — Ingests multi-page contract PDFs via Apache Tika and SEC regulatory data.

- 🧠 **LLM Entity & Relationship Extraction** — Uses Spring AI and GPT-4o to extract typed entities (Contract, Clause, Party) and graph relations (DEPENDS_ON, CONFLICTS_WITH).

- 🕸️ **GraphRAG Engine** — Combines multi-hop Cypher graph traversals with vector similarity search for grounded answers.

- 🔎 **Full Explainability Trace** — Returns real-time execution thoughts detailing graph expansion and retained context.

- ☁️ **GCP Serverless & Private Networking** — Spring Boot backend deployed on GCP Cloud Run inside lexintel-vpc, connecting privately to a standalone Neo4j Docker container on a Compute Engine VM (10.8.0.0/28 range).

- 🏗️ **Infrastructure as Code (IaC)** — Complete GCP networking, firewall rules, Cloud SQL PostgreSQL, and VM provisioning automated via Terraform (cloudsql.tf, vpc.tf, compute_neo4j.tf).


## Screenshots

| LexIntel Dashboard with Explainability section|
|---|
| ![LexIntel dashboard with thought trace](docs/screenshots/graphrag-query.jpg) | 




## Architecture

```

Vercel Edge CDN (Next.js App Router + Vercel AI SDK + Clerk)
    │
    │   HTTPS / SSE Stream Proxy
    ▼
GCP Cloud Run Service (Spring Boot 3.3.2 / Java 21)
    │
    ├── (Private VPC Ingress: 10.8.0.0/28) ──▶ GCP Compute Engine VM 
    │                                           (Standalone Neo4j Docker in lexintel-vpc)
    │
    └── (Cloud SQL Auth Proxy)             ──▶ GCP Cloud SQL 
                                                (PostgreSQL Relational Storage)

```
## Tech Stack

| Layer | Technology |
|---|---|
| Frontend Framework| Next.js App Router, React 18, TypeScript
| Styling & Motion	| Tailwind CSS, Framer Motion
| Authentication	| Clerk (@clerk/nextjs)
| Telemetry & Observability	| Vercel Speed Insights & Analytics
| Backend Runtime	| Java 21 (Virtual Threads), Spring Boot 3.3.2
| AI Orchestration	| Spring AI 1.0.0-M1, OpenAI GPT-4o
| Graph Database	| Neo4j 5.18 Enterprise (Graph + Vector Search, APOC)
| Relational Database	| GCP Cloud SQL PostgreSQL
| Document Parsing	| Apache Tika 2.9.2
| Cloud & Hosting	| Vercel (Frontend), GCP Cloud Run & Compute Engine (Backend & Graph DB)
| Infrastructure as Code	| Terraform, GitHub Actions CI/CD

## Local Development & Setup

### Prerequisites

- Node.js 18+ and npm / pnpm
- Java 21 (JDK) and Maven (or ./mvnw)
- Docker & Docker Desktop
- Google Cloud SDK (gcloud)

### 1. Local Database Setup (Docker Compose)
For local development, spin up local PostgreSQL and Neo4j instances using the root docker-compose.yml:

```bash
docker-compose up -d
```

- Neo4j Browser: http://localhost:7474 (`neo4j / password123`)

- Neo4j Bolt Port: `7687`


### 2. Backend Setup (Spring Boot)

Set your environment variables and start the backend service:

```bash
export OPENAI_API_KEY=sk-...
export SPRING_NEO4J_URI=bolt://localhost:7687
export SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j
export SPRING_NEO4J_AUTHENTICATION_PASSWORD=password123

./mvnw spring-boot:run
```

The backend starts at http://localhost:8080.

### 3. Frontend Setup (Next.js)
Navigate to the frontend directory, configure environment variables in `.env.local`, and start the development server:
```bash
# Set Clerk Keys in .env.local
NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=pk_test_...
CLERK_SECRET_KEY=sk_test_...
BACKEND_URL=http://localhost:8080

npm install
npm run dev
```
Open http://localhost:3000 in your browser.


## API Reference
All REST endpoints are exposed under the /api/v1/legal base path.
| Method | Endpoint | Description |
|---|---|---|
|`POST`| `/contracts/ingest` | **Multipart PDF Upload.** Parses PDF via Apache Tika, extracts structured entities (`Contract`, `Clause`, `Party`) via GPT-4o, and executes a two-pass node/edge builder in Neo4j. Returns the generated contractId. |
|`GET`|`/contracts/{contractId}/graph`|**Graph Visualization Payload.** Fetches a 2-hop sub-graph around the specified contract and returns a Cytoscape-compatible `GraphDataResponse` containing nodes and directed `DEPENDS_ON` edges.|
|`POST`|`/query`|**GraphRAG Search & Synthesis.** Body: `{ "question": "..." }`. Runs Cypher pattern-matching graph traversal + vector search, executing tool calls (e.g., SEC EDGAR) as needed, and returns a grounded answer with explainability traces.|

Example Usage
```bash
# 1. Ingest a contract PDF and extract entities/relationships
curl -X POST http://localhost:8080/api/v1/legal/contracts/ingest \
  -F "file=@sample_contract.pdf"

# Response: { "contractId": "ctr_8f9a2b1c", "status": "SUCCESS" }

# 2. Fetch the 2-hop sub-graph data for Cytoscape UI rendering
curl -X GET http://localhost:8080/api/v1/legal/contracts/ctr_8f9a2b1c/graph

# 3. Query the GraphRAG engine
curl -X POST http://localhost:8080/api/v1/legal/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What triggers immediate termination under this contract?"}'
```

## Developer SSH Tunneling for Production Neo4j

In production, the GCP Compute Engine VM hosting Neo4j restricts ingress strictly to the Cloud Run VPC connector (`10.8.0.0/28`). External IP access is blocked for security.

To access the production Neo4j Browser (`7474`) or Bolt port (`7687`) from your local machine, open an SSH port-forwarding tunnel:

```bash
gcloud compute ssh lexintel-neo4j-vm \
    --zone=us-central1-a \
    -- -L 7474:localhost:7474 -L 7687:localhost:7687
```
Once connected, open http://localhost:7474 in your browser.

## Infrastructure Management (Terraform)
Infrastructure provisioning for GCP (`lexintel-vpc`, Cloud SQL, Compute Engine, Secrets, and IAM) is managed in the repository root using Terraform:

```bash
# Initialize and review changes
terraform init
terraform plan

# Apply infrastructure changes
terraform apply
```

Note: Automated CI/CD deployments trigger on pushes to `main`. Append [`skip ci`] to commit messages when updating infrastructure configuration without triggering pipeline runs.


## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.
