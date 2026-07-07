# Confluence RAG Ingestor (Spring Boot 4 / Spring AI 2)

Java 21 / Spring Boot 4.0 / Spring AI 2.0 migration of the Python `confluence-pdf-export-poc` toward a Confluence → Markdown → chunks → ChromaDB RAG pipeline.

**Phase 10** completes retry/resume, `batch-progress.json`, manifest state preservation on rebuild, and enriched status reporting.

See [MIGRATION_PLAN.md](MIGRATION_PLAN.md) for the full 10-phase roadmap (all phases complete).

**Agent / AI documentation:** [AGENTS.md](AGENTS.md) — parent index with per-package child docs in [docs/agent/](docs/agent/) (use this before scanning source).

**Debugging:** [debug_help.md](debug_help.md) — run, logs, Jaeger traces, Swagger.  
Full reference: [docs/agent/observability.md](docs/agent/observability.md).

## Prerequisites

- JDK 21+
- Maven 3.9+
- **Runtime:** [Ollama](https://ollama.com/) with `nomic-embed-text`, [ChromaDB](https://www.trychroma.com/) on `localhost:8000` (e.g. Podman)

Stack: Spring Boot **4.0.3**, Spring AI **2.0.0**, springdoc-openapi **3.0.3**.

## Build & run

### Backend only (API / Swagger)

```bash
cd confluence-rag-ingestor
mvn spring-boot:run
```

Skip the React build during Maven (faster iteration):

```bash
mvn spring-boot:run -Dskip.frontend=true
```

### React UI — development mode

Terminal 1 — Spring Boot API on port **8080**:

```bash
mvn spring-boot:run -Dskip.frontend=true
```

Terminal 2 — Vite dev server on port **5173** (proxies `/api` and `/health` to Spring Boot):

```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173)

### Production JAR (React + API in one artifact)

Builds the React app, copies `frontend/dist` into `classpath:/static/`, and packages a single executable JAR:

```bash
mvn clean package
java -jar target/confluence-rag-ingestor-0.1.0-SNAPSHOT.jar
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080) for the dashboard.

### Endpoints

- **Dashboard UI:** [http://127.0.0.1:8080/](http://127.0.0.1:8080/)
- Health: [http://127.0.0.1:8080/health](http://127.0.0.1:8080/health)
- OpenAPI: [http://127.0.0.1:8080/swagger-ui.html](http://127.0.0.1:8080/swagger-ui.html)
- Logs: `logs/app.log`

## API

### Dashboard UI APIs (`/api/*`)

These endpoints power the React dashboard. Legacy ingestion/query endpoints under `/api/confluence/*` remain available.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/config` | Current runtime config (PAT masked) |
| `POST` | `/api/config` | Save runtime config |
| `POST` | `/api/confluence/test` | Test Confluence PAT + target page/space |
| `POST` | `/api/ingest` | Start full ingestion pipeline using saved config |
| `GET` | `/api/ingest/status` | Ingestion status (`?parentPageId=` optional) |
| `POST` | `/api/chat` | RAG chat — retrieves chunks + LLM answer with sources |

#### Save configuration

```bash
curl -X POST "http://localhost:8080/api/config" ^
  -H "Content-Type: application/json" ^
  -d "{\"confluenceBaseUrl\":\"https://confluence.example.com\",\"confluenceTarget\":\"12345\",\"pat\":\"YOUR_PAT\",\"llmModel\":\"llama3.2\",\"vectorStore\":\"chroma\"}"
```

`GET /api/config` returns `maskedPat` like `****abcd` — never the full token.

#### Test Confluence connection

```bash
curl -X POST "http://localhost:8080/api/confluence/test" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"confluenceTarget\":\"12345\",\"pat\":\"YOUR_PAT\"}"
```

#### Start ingestion (dashboard)

```bash
curl -X POST "http://localhost:8080/api/ingest" -H "Content-Type: application/json" -d "{}"
```

Runs crawl → markdown → chunk → vector ingest using saved configuration.

#### Ask a question (RAG chat)

```bash
curl -X POST "http://localhost:8080/api/chat" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"How do I configure SSL verification?\",\"parentPageId\":\"12345\"}"
```

Requires Ollama with a chat model (default `llama3.2`) and ingested vectors in ChromaDB.

### Security — PAT / API token handling

- **Browser:** The React UI never stores PATs in `localStorage` or `sessionStorage`. Tokens are typed into a password field and sent to the backend over HTTP only.
- **API responses:** `GET /api/config` always returns a masked token (`****` + last 4 chars) or `patConfigured: false`.
- **Server memory:** PAT is held in the JVM heap after save; restart clears it unless re-supplied.
- **Optional persistence:** Set `CONFLUENCE_INGESTOR_ENCRYPTION_KEY` (Base64-encoded 32-byte AES key) to persist an encrypted PAT in `data/ui-config.json`. Without this key, non-secret config may be saved but the PAT stays memory-only.
- **Environment variables:** `CONFLUENCE_PAT`, `CONFLUENCE_BASE_URL`, and `CONFLUENCE_TARGET` can seed config at startup.
- **Logging:** PAT values are never written to logs (existing ingestion invariant).

Generate an encryption key (PowerShell):

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

### Legacy ingestion API (`/api/confluence/*`)

### Initialize ingestion workspace (empty manifest)

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"forceRebuildManifest\":false}"
```

Creates `data/{parentPageId}/manifest.json` (empty pages list) when missing.

### Crawl Confluence tree and build manifest (Phase 2)

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"forceRebuildManifest\":true}"
```

Returns `202 Accepted` and runs the crawl in the background. Poll status until `crawl-progress.json` shows `COMPLETED`.

A second concurrent crawl for the same `parentPageId` returns `409 Conflict` with `ALREADY_RUNNING`.

### Extract pages to Markdown (Phase 3)

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"extractMarkdown\":true}"
```

Processes pending manifest pages in the background (batched by `batchSize` / `concurrency`). Writes:

```
data/{parentPageId}/pages/{pageId}/page.md
data/{parentPageId}/pages/{pageId}/metadata.json
data/{parentPageId}/pages/{pageId}/assets/
```

Combine with manifest crawl:

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"forceRebuildManifest\":true,\"extractMarkdown\":true}"
```

Markdown extraction starts automatically after the crawl completes.

Referenced images (`ac:image` / `ri:attachment`) are downloaded into `assets/` during `extractMarkdown` (Phase 4). Allowed extensions are configured via `confluence.ingestor.allowed-image-extensions`.

Complex tables with merged or nested cells are extracted to `assets/tables/*.json` with Markdown placeholders like `[TABLE:table-1](assets/tables/table-1.json)` (Phase 5).

Draw.io macros are extracted to `assets/diagrams/` with label text inlined in Markdown (Phase 6). Allowed diagram extensions: `confluence.ingestor.allowed-drawio-extensions`.

### Chunk pages for RAG (Phase 7)

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"chunkMarkdown\":true}"
```

Writes heading-scoped chunks to `data/{parentPageId}/chunks/{pageId}.jsonl`. Combine with extraction:

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"extractMarkdown\":true,\"chunkMarkdown\":true}"
```

Chunking starts automatically after Markdown extraction completes. Max chunk size: `confluence.ingestor.max-chunk-characters` (default 4000).

### Ingest chunks into ChromaDB (Phase 8)

Start ChromaDB and Ollama first, then:

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"ingestVectors\":true}"
```

Loads chunked pages into the Chroma collection (`confluence.ingestor.chroma-collection-name`, default `confluence-rag`). Full pipeline:

```bash
curl -X POST "http://localhost:8080/api/confluence/ingest" ^
  -H "Content-Type: application/json" ^
  -d "{\"baseUrl\":\"https://confluence.example.com\",\"parentPageId\":\"12345\",\"pat\":\"YOUR_PAT\",\"extractMarkdown\":true,\"chunkMarkdown\":true,\"ingestVectors\":true}"
```

Vector ingest starts automatically after chunking completes. Disable with `confluence.ingestor.vector-ingest-enabled=false`.

### Query ingested content (Phase 9)

Semantic search over ChromaDB (no PAT — local vector store only):

```bash
curl -X POST "http://localhost:8080/api/confluence/query" ^
  -H "Content-Type: application/json" ^
  -d "{\"query\":\"how do I configure SSL verification\",\"parentPageId\":\"12345\",\"topK\":5}"
```

Each hit includes `webUrl`, `title`, `headingPath`, chunk `text`, and a similarity `score`. Optional `parentPageId` scopes results to one Confluence tree. Defaults: `confluence.ingestor.default-query-top-k` (5), `default-similarity-threshold` (0.0 = accept all).

### Status (no auth — local disk only)

```bash
curl "http://localhost:8080/api/confluence/ingest/status/12345"
```

Returns manifest counts, `crawl-progress.json`, `batch-progress.json` (when a batch job has run), and `activeJob` when a background job is in progress for that `parentPageId`.

`batch-progress.json` fields: `phase` (`PAGE_TRANSFORM` | `CHUNK` | `VECTOR_INGEST`), `status` (`RUNNING` | `COMPLETED` | `FAILED`), `processedCount`, `failedCount`, `totalPending`, `currentPageId`, `startedAt`, `error`.

Manifest rebuild (`forceRebuildManifest=true`) preserves per-page ingestion state (`markdownExtracted`, `chunked`, `vectorIngested`, paths, retries) for pages that still exist after the crawl.

## On-disk layout

```
data/{parentPageId}/
├── manifest.json
├── crawl-progress.json
├── batch-progress.json     (Phase 10 — last batch job progress)
├── chunks/                 (Phase 7 — per-page JSONL)
│   └── {pageId}.jsonl
└── pages/{pageId}/
    ├── page.md             (Phase 3+)
    ├── metadata.json
    └── assets/
        ├── (images)
        ├── tables/         (Phase 5 — complex table JSON)
        └── diagrams/       (Phase 6 — draw.io XML + JSON)
```

## Configuration

`application.yml` → `confluence.ingestor.*` (data directory, batch defaults, SSL verification, retry threshold, vector ingest, query defaults).

Spring AI settings under `spring.ai.*`:

- `spring.ai.ollama.embedding.model` — embedding model (default `nomic-embed-text`)
- `spring.ai.vectorstore.chroma.client.host` / `port` — ChromaDB endpoint (default `localhost:8000`)
- `spring.ai.vectorstore.chroma.collection-name` — synced with `confluence.ingestor.chroma-collection-name`
