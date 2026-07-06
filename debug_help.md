# Debug help — Confluence RAG Ingestor

Quick reference for local debugging. Full detail: [docs/agent/observability.md](docs/agent/observability.md).

---

## 1. Run the app and tail logs

```bash
cd confluence-rag-ingestor
mvn spring-boot:run
```

Watch log output:

- **Console** — Spring Boot stdout
- **File** — `logs/app.log`

Log lines include `traceId` and `spanId` for correlation with Jaeger:

```
INFO [confluence-rag-ingestor,a1b2c3d4e5f6g7h8,i9j0k1l2m3n4] Ingest request parentPageId=12345 ...
```

**Useful grep patterns** (`logs/app.log`):

| Pattern | Meaning |
|---------|---------|
| `Ingest request` | HTTP ingest entry |
| `In-process job published` | Background stage started |
| `Background manifest crawl` | Crawl lifecycle |
| `page transform` / `chunk batch` / `vector ingest` | Batch stages |
| `already running` / `rejected` | Job mutex (409 conflict) |
| `failed` / `error` | Failures — check `lastError` in manifest |

---

## 2. More detail (Confluence HTTP, Chroma, chunking)

Activate the **local** profile and raise log levels for noisy packages.

Add to `src/main/resources/application-local.yml` (or pass at startup):

```yaml
logging:
  level:
    com.confluence.ingestor.confluence: DEBUG
    com.confluence.ingestor.rag: DEBUG
    com.confluence.ingestor.transform: DEBUG
```

Run with profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`local` also sets `default-concurrency: 1` and `per-page-state-enabled: true` (Windows-friendly).

---

## 3. View traces (Jaeger)

OTLP export is configured in `application.yml` → `management.otlp.tracing.endpoint: http://localhost:4318/v1/traces`.

Start Jaeger:

```bash
docker run -d --name jaeger -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest
```

Open **http://localhost:16686** → select service **`confluence-rag-ingestor`**.

Copy **`traceId`** from any log line → search in Jaeger to see the full request span tree.

Example spans: `api.ingest`, `ingestion.manifest.crawl`, `page.transform`, `rag.chroma.ingest`, `query.search`.

---

## 4. Swagger

Interactive API docs (triggers controller INFO logs):

| Resource | URL |
|----------|-----|
| Swagger UI | http://127.0.0.1:8080/swagger-ui.html |
| OpenAPI JSON | http://127.0.0.1:8080/api-docs |

**Poll status** (no PAT):

```bash
curl "http://localhost:8080/api/confluence/ingest/status/{parentPageId}"
```

---

## On-disk checkpoints (no auth)

```
data/{parentPageId}/
├── manifest.json          # per-page lastError, noOfRetries
├── crawl-progress.json      # crawl RUNNING/COMPLETED/FAILED
└── batch-progress.json      # PAGE_TRANSFORM | CHUNK | VECTOR_INGEST
```

---

## Common issues

| Symptom | What to check |
|---------|----------------|
| **409 ALREADY_RUNNING** | Status API `activeJob`; wait or restart app |
| **Crawl stuck** | `crawl-progress.json` `currentPageId`; Confluence DEBUG logs |
| **Pages skipped** | manifest `noOfRetries >= 2` (threshold in config) |
| **Query 503** | ChromaDB on `:8000`, Ollama on `:11434` |
| **Slow on Windows** | `mvn spring-boot:run -Dspring-boot.run.profiles=local` |

---

## Related docs

- [docs/agent/observability.md](docs/agent/observability.md) — full logging/tracing/Swagger reference
- [AGENTS.md](AGENTS.md) — package index for code navigation
- [README.md](README.md) — API examples and prerequisites
