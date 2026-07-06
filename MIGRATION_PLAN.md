# Confluence RAG Ingestor — Phased Migration Plan

Migration from Python FastAPI POC (`confluence-pdf-export-poc`) to Java 21 / Spring Boot 4.x / Spring AI 2.x.

## Source POC Summary

| Aspect | Python POC |
|--------|------------|
| Framework | FastAPI + Uvicorn |
| Auth | Bearer PAT |
| Primary output | PDF (`flyingpdf` + Playwright HTML fallback) |
| On-disk layout | `data/{parentPageId}/manifest.json`, `crawl-progress.json`, `batch-progress.json`, `pdf/` |
| Concurrency | ThreadPoolExecutor, per-thread HTTP sessions |
| Progress | Background tasks, atomic JSON writes, retry/skip policy (`noOfRetries >= 2`) |

## Target Architecture

```
Confluence REST (body.storage / body.view)
    → HTML → Markdown + tables + images + draw.io
    → per-page assets + metadata.json
    → heading-based chunks
    → embeddings → ChromaDB (local Podman)
    → RAG query API
```

## Stack (current)

| Layer | Version |
|-------|---------|
| Java | 21 |
| Spring Boot | 4.0.x |
| Spring AI | 2.0.x |
| springdoc-openapi | 3.0.x |
| Vector store | ChromaDB (local) |
| Embeddings | Ollama (`nomic-embed-text`) |

## Architecture Principles (Post–Phase 10)

These govern Phases 11–13 and all new code.

1. **SOLID** — Thin controllers; orchestration in application services; domain logic inside bounded packages; persistence behind repositories/ports; depend on abstractions for Confluence, vector store, and messaging (not concrete batch services across boundaries).

2. **Neutral package naming** — No vendor-specific prefix in Java packages or Maven `groupId`. Current code still uses `com.confluence.ingestor` (technical debt); Phase 11 renames to a neutral base (e.g. `com.confluence.ingestor` or your org namespace). Logging config and test packages must follow the same base.

3. **Monolith today, microservices tomorrow** — Keep strict package boundaries that map 1:1 to future deployables (see table below). No cross-package access to concrete implementations; use ports, DTO contracts, or domain events at boundaries.

4. **Concurrency evolution** — **Now (Phase 11):** in-process `@Async` and thread pools for background batches (`ingestionTaskExecutor`, virtual threads per batch for Confluence HTTP). **Later (Phase 12):** Kafka (or Spring Cloud Stream) for job handoff between stages; in-process threads only inside each consumer.

5. **Local dev performance** — High concurrency against shared `manifest.json` and progress files causes lock contention and slow runs on Windows. Phase 11 adopts a local-friendly strategy (see [Local concurrency options](#local-concurrency-options-phase-11)).

6. **Observability** — OpenTelemetry-compatible metrics, logging, and distributed tracing (Micrometer + OTel exporter). Correlation/trace IDs on ingest and query paths. Structured logs suitable for log aggregation.

## Monolith Package Layout (current → target boundaries)

| Package | Role | SOLID notes |
|---------|------|-------------|
| `api` | REST controllers, request/response DTOs | Thin; validation only |
| `config` | Properties, async executor, future OTel | Infrastructure wiring |
| `confluence` | REST client, crawler, attachments | Single responsibility: Confluence I/O |
| `transform` | HTML→Markdown, tables, draw.io | Pure transformation; no storage |
| `storage` | Manifest, progress, atomic file I/O | Persistence; extract interfaces in Phase 11 |
| `model` | Manifest/chunk domain models | Shared contracts |
| `service` | Orchestration, batch jobs, coordinators | Split by stage in Phase 11; reduce god-service |
| `rag` | Chunking, embedding, Chroma query/ingest | Vector/RAG boundary |

**Known coupling to address in Phase 11:** `IngestionService` orchestrates all stages; batch services chain directly (`PageTransformBatchService` → `ChunkBatchService` → `VectorIngestBatchService`); `ManifestService` combines locking, I/O, and business rules; `IngestionJobCoordinator` is in-memory only.

## Package → Future Microservice Map

| Monolith package | Future service | Responsibility |
|------------------|----------------|----------------|
| `confluence` | **confluence-connector** | REST client, tree crawl, attachments |
| `transform` | **content-transform** | HTML→Markdown, tables, draw.io |
| `storage` | **ingestion-state** (or shared lib) | Manifest, progress, durable job state |
| `rag` + vector ingest services | **embedding-indexer** | Chunking, embed, Chroma upsert |
| `api` (ingest) + ingest `service` | **ingestion-orchestrator** | Pipeline coordination, status API |
| `api` (query) + `QueryService` | **query-api** | Semantic search over vector store |

Shared `model` / API DTOs → future **`ingestion-contracts`** JAR or schema registry (Avro/JSON Schema when Kafka lands).

## Phase Map

| Phase | Scope | Status |
|-------|--------|--------|
| **1** | Spring Boot skeleton, DTOs, models, storage services, health + ingest + status endpoints (no Confluence) | **Done** |
| **2** | `ConfluenceClient`, `PageCrawler`, child pagination, manifest crawl | **Done** |
| **3** | Fetch `body.storage`, JSoup HTML→Markdown, `page.md` + `metadata.json` | **Done** |
| **4** | `AttachmentClient`, image download, local asset links in Markdown | **Done** |
| **5** | `TableExtractor`, complex table placeholders | **Done** |
| **6** | `DrawioExtractor`, diagram assets + text extraction | **Done** |
| **7** | `MarkdownChunker`, chunk JSONL | **Done** |
| **8** | `EmbeddingService`, `ChromaIngestionService` (Spring AI + Ollama + ChromaDB) | **Done** |
| **9** | Query API over ChromaDB with source page links | **Done** |
| **10** | Retry/resume, progress files, batch execution, Windows-safe atomic writes, job mutual exclusion | **Done** |
| **11** | Architecture hardening: package rename, SOLID/ports, local concurrency strategy, OpenTelemetry baseline | **Done** |
| **12** | Kafka integration: job topics, idempotent consumers, replace in-process coordinator | **Done** |
| **13** | Service extraction: multi-module or multi-repo deployables per package map | **Done** (reactor layout; see below) |

## Phase 11 — Architecture Hardening (detail)

### 11a — Package and build rename

- Rename Java base package (remove `netcracker`); update Maven `groupId`, `application.yml` logging package, test sources, and imports.
- No functional change; full test suite must stay green.

### 11b — SOLID refactors

- Introduce ports/adapters, e.g. `ConfluencePort`, `ManifestRepository`, `VectorStorePort`, `JobPublisher` (no-op in-process impl until Phase 12).
- Split `IngestionService` into stage-specific application services behind a thin facade.
- Narrow `ManifestService` to repository + separate lock/coordination policy.

### 11c — Local concurrency options

Pick one or combine for local runs (bottleneck is shared file writes, not CPU threads):

| Option | Description | Effort |
|--------|-------------|--------|
| **A. Local profile** | `concurrency=1`, smaller async pool (`ingestionTaskExecutor`) | Low |
| **B. Per-page state files** | `pages/{pageId}/ingestion-state.json`; merge into manifest at stage end | Medium |
| **C. Embedded DB** | H2/SQLite for manifest + job queue; removes JSON rewrite storms | Medium–High |
| **D. Single-writer queue** | Producers enqueue page IDs; one thread updates manifest (Kafka preview) | Medium |

Virtual threads inside a batch remain appropriate for Confluence HTTP parallelism once manifest write contention is reduced.

### 11d — OpenTelemetry baseline

- Add `spring-boot-starter-actuator`, Micrometer tracing bridge, OTel exporter (OTLP or Zipkin for local).
- Structured logging with `traceId` / `spanId`.
- Spans on: ingest/query controllers, batch stage services, `ConfluenceClient` calls, vector store read/write.
- **Current gap:** no OTel/Micrometer tracing dependencies in the project today.

## Phase 12 — Kafka Integration (detail)

- Topics (example): `ingestion.manifest.crawl`, `ingestion.page.transform`, `ingestion.page.chunk`, `ingestion.page.vector`, plus DLQ per stage.
- Message key: `parentPageId` (and optionally `pageId`) for ordering per tree.
- Replace `IngestionJobCoordinator` with partition-aware consumers; idempotent handlers.
- Implement `JobPublisher` Kafka adapter; retain no-op adapter for single-process tests.
- Progress/manifest: either keep on-disk layout with event-driven updates or move to DB (decision from Phase 11c).

## Phase 13 — Microservice Split (detail)

- Maven reactor or separate repos per [Package → Future Microservice Map](#package--future-microservice-map).
- Shared **`ingestion-contracts`** module only (DTOs, event schemas)—no shared service implementations.
- Each service owns its datastore and config; orchestrator publishes commands; workers emit progress events.
- Chroma/query and Confluence credentials isolated per service.

## Multi-Agent Execution Strategy (Phases 11–13)

Use multiple agents only with clear sequencing; run `mvn test` after each wave.

| Step | Agent focus | Parallel? |
|------|-------------|-----------|
| 1 | Package + `groupId` rename (11a) | **Single agent first** — touches every file |
| 2 | OTel/actuator setup (11d) | Parallel with step 3 if rename is merged |
| 3 | Ports/interfaces per bounded package (11b) | 2–3 agents: `confluence`, `transform`, `rag`, `storage` |
| 4 | Manifest/concurrency strategy (11c) | **Single architect agent** — cross-cutting |
| 5 | Kafka adapters + Testcontainers (12) | After ports + `JobPublisher` exist |
| 6 | Module/repo split (13) | Sequential after Kafka contracts stable |

**Integration agent** after each wave: full test suite + smoke ingest/query path.

## Python → Java Module Mapping

| Python module | Java package (target) |
|---------------|----------------------|
| `routes.py` | `api.IngestionController` |
| `export_service.py` | `service` orchestration (future `ingestion-orchestrator`) |
| `confluence_client.py` | `confluence.ConfluenceClient`, `PageCrawler` |
| `manifest_service.py` | `storage.ManifestService`, `storage.FileStorageService` |
| `crawl_progress_service.py` | `storage.CrawlProgressService` |
| `batch-progress` (Python) | `storage.BatchProgressService` |
| `html_to_pdf_service.py` | **Replaced** by `transform.HtmlToMarkdownService` |
| `pdf_service.py` | **Replaced** by page/asset storage |
| `kdb_upload_service.py` | **Replaced** by `rag.ChromaIngestionService` |

## On-Disk Artifacts (Target)

```
data/{parentPageId}/
├── manifest.json
├── crawl-progress.json
├── batch-progress.json   (Phase 10 — transform/chunk/vector batch jobs)
├── pages/{pageId}/
│   ├── page.md
│   ├── metadata.json
│   ├── assets/
│   └── ingestion-state.json   (Phase 11 option B — per-page state)
└── chunks/
    └── {pageId}.jsonl
```

## Confluence API Endpoints (Phases 2–6)

- `GET /rest/api/content/{pageId}?expand=body.storage,body.view,version,space,ancestors`
- `GET /rest/api/content/{pageId}/child/page` (paginated)
- `GET /rest/api/content/{pageId}/child/attachment`

## Invariants Carried Forward

1. Bearer PAT only — never log token values.
2. Manifest + progress + retry concepts preserved (`noOfRetries`, skip threshold).
3. Depth-first child crawl (no `/descendant/page` — 501 on many DC builds).
4. Windows-safe atomic file replace with retries.
5. Status endpoint reads local disk only (no auth).
6. Background work remains non-blocking for HTTP callers (threads now; Kafka later).
7. Observability must not leak PAT or page content into trace attributes by default.

## Phase 13 — Multi-Module Layout (implemented)

Runnable Spring Boot app: root `pom.xml` builds `ingestion-orchestrator` (canonical `src/`).

Staged library source trees (future independent JARs / deployables):

| Directory | Future artifact |
|-----------|-----------------|
| `ingestion-contracts/` | Shared DTOs, models, ports, job message types |
| `confluence-connector/` | Confluence REST client + crawler |
| `content-transform/` | HTML→Markdown, tables, draw.io |
| `ingestion-state/` | Manifest, progress, atomic file I/O |
| `embedding-indexer/` | Chunking, embedding, Chroma ingest |
| `query-api/` | Query REST + semantic search |
| `ingestion-orchestrator/` | Ingest REST, Kafka adapters, pipeline coordination |

Sync staged trees from `src/main/java` when extracting a module to its own Maven artifact.
