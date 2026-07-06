# Confluence RAG Ingestor — Agent Index

**Read this file first.** Do not scan the full codebase unless the child doc below does not answer the question.

Canonical source: `src/main/java/com/confluence/ingestor/` (94 Java files).  
Runnable app entry: `ConfluenceRagIngestorApplication.java`.  
User docs: [README.md](README.md) · Migration history: [MIGRATION_PLAN.md](MIGRATION_PLAN.md).

---

## How to use this index

1. Match the question to **Quick lookup** below.
2. Open the linked child doc in `docs/agent/`.
3. Open only the specific source file(s) listed in that child doc.
4. Staged multi-module trees (`ingestion-orchestrator/`, `confluence-connector/`, etc.) mirror `src/` for future extraction — **prefer `src/`** unless asked about module split.

---

## Quick lookup (question → doc → key files)

| If the question is about… | Read | Primary files |
|---------------------------|------|---------------|
| REST endpoints, request/response DTOs, HTTP status codes | [docs/agent/api.md](docs/agent/api.md) | `api/IngestionController.java`, `api/QueryController.java` |
| `application.yml`, properties, async pool, Kafka toggle | [docs/agent/config.md](docs/agent/config.md) | `config/IngestorProperties.java`, `resources/application.yml` |
| Confluence REST, crawl, attachments, PAT auth | [docs/agent/confluence.md](docs/agent/confluence.md) | `confluence/ConfluenceClient.java`, `confluence/PageCrawler.java` |
| HTML→Markdown, tables, draw.io, images in storage XML | [docs/agent/transform.md](docs/agent/transform.md) | `transform/HtmlToMarkdownService.java` |
| manifest.json, progress files, atomic disk I/O | [docs/agent/storage.md](docs/agent/storage.md) | `storage/ManifestService.java`, `storage/FileStorageService.java` |
| Domain models (manifest, chunks, page docs) | [docs/agent/model.md](docs/agent/model.md) | `model/PageManifestEntry.java`, `model/ChunkDocument.java` |
| Ports/interfaces (SOLID boundaries) | [docs/agent/port.md](docs/agent/port.md) | `port/ConfluencePort.java`, `port/JobPublisher.java` |
| Kafka topics, job messages, in-process vs Kafka publisher | [docs/agent/messaging.md](docs/agent/messaging.md) | `messaging/InProcessJobPublisher.java`, `messaging/KafkaJobPublisher.java` |
| Chunking, embeddings, Chroma ingest/query | [docs/agent/rag.md](docs/agent/rag.md) | `rag/MarkdownChunker.java`, `rag/ChromaIngestionService.java` |
| Pipeline orchestration, batch jobs, job mutex | [docs/agent/service.md](docs/agent/service.md) | `service/IngestionService.java`, `service/*BatchService.java` |
| End-to-end flow, phases, on-disk layout, architecture | [docs/agent/architecture.md](docs/agent/architecture.md) | — |
| **Logging, tracing, Swagger, debugging** | [docs/agent/observability.md](docs/agent/observability.md) | `application.yml`, `TracingConfig.java` |

---

## Project summary

Spring Boot 4 monolith that ingests a Confluence page tree into a local RAG pipeline:

```
Confluence REST → Markdown + assets → heading chunks → Ollama embeddings → ChromaDB → query API
```

| Item | Value |
|------|-------|
| Java | 21 |
| Spring Boot | 4.0.3 |
| Spring AI | 2.0.0 |
| Build | Maven (`pom.xml`, single JAR) |
| Port | 8080 |
| Data dir | `data/{parentPageId}/` (configurable) |
| Embeddings | Ollama `nomic-embed-text` @ `localhost:11434` |
| Vector store | ChromaDB @ `localhost:8000` |
| Kafka | **Off by default** (`confluence.ingestor.kafka.enabled=false`) |

---

## Pipeline stages (ingestion flags)

`POST /api/confluence/ingest` — flags are **mutually prioritized** (first match wins in `IngestionService`):

1. `forceRebuildManifest=true` → crawl tree → `manifest.json`
2. `extractMarkdown=true` → `page.md` + `metadata.json` + `assets/`
3. `chunkMarkdown=true` → `chunks/{pageId}.jsonl`
4. `ingestVectors=true` → ChromaDB collection
5. *(none)* → init empty manifest or return existing summary

Batch stages chain automatically when multiple flags are set on one request (crawl → transform → chunk → vector).

---

## Package tree → child docs

```
com.confluence.ingestor
├── api/              → docs/agent/api.md
├── config/           → docs/agent/config.md
├── confluence/       → docs/agent/confluence.md
├── transform/        → docs/agent/transform.md
├── storage/          → docs/agent/storage.md
├── model/            → docs/agent/model.md
├── port/             → docs/agent/port.md
├── messaging/        → docs/agent/messaging.md
├── rag/              → docs/agent/rag.md
└── service/          → docs/agent/service.md
```

Root application: `ConfluenceRagIngestorApplication.java`  
Cross-cutting architecture: [docs/agent/architecture.md](docs/agent/architecture.md)  
**Logging, tracing, Swagger:** [docs/agent/observability.md](docs/agent/observability.md)  
**Quick debug guide:** [debug_help.md](debug_help.md)

---

## Staged module directories (Phase 13)

Future deployables — source copies, not separate Maven modules yet:

| Directory | Maps to package(s) | Future service |
|-----------|-------------------|----------------|
| `ingestion-contracts/` | `model`, `port`, `messaging` | Shared JAR |
| `confluence-connector/` | `confluence` | confluence-connector |
| `content-transform/` | `transform` | content-transform |
| `ingestion-state/` | `storage` | ingestion-state |
| `embedding-indexer/` | `rag` | embedding-indexer |
| `ingestion-orchestrator/` | `api`, `config`, `service`, `messaging` | ingestion-orchestrator |
| `query-api/` | `api` (query), `service/QueryService` | query-api |

Sync from `src/main/java` when extracting a module.

---

## Tests (by area)

| Area | Location |
|------|----------|
| Phase integration (1–10) | `src/test/java/.../IngestionPhase*IntegrationTest.java` |
| Confluence client/crawler | `src/test/java/.../confluence/*Test.java` |
| Transform | `src/test/java/.../transform/*Test.java` |
| RAG | `src/test/java/.../rag/*Test.java` |
| Storage/manifest merge | `src/test/java/.../storage/ManifestServiceMergeTest.java` |
| Kafka publisher | `src/test/java/.../messaging/KafkaJobPublisherIntegrationTest.java` |

Run: `mvn test`

---

## Invariants (do not break)

1. Bearer PAT only — **never log** `pat` values.
2. Depth-first child crawl via `/child/page` (not `/descendant/page`).
3. Windows-safe atomic file replace with retries (`FileStorageService`).
4. Status endpoint reads local disk only — no Confluence auth.
5. `retry-skip-threshold` (default 2): pages with `noOfRetries >= threshold` are skipped in batches.
6. Manifest rebuild preserves per-page ingestion state for pages that still exist.
