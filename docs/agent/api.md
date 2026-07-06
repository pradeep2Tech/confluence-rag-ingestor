# Package: `com.confluence.ingestor.api`

Parent index: [AGENTS.md](../../AGENTS.md)

REST layer — thin controllers, validation, DTO mapping. No business logic.

## Controllers

| Class | Endpoints | Delegates to |
|-------|-----------|--------------|
| `IngestionController` | `GET /health` | static map |
| | `POST /api/confluence/ingest` | `IngestionService.startIngestion` |
| | `GET /api/confluence/ingest/status/{parentPageId}` | `IngestionService.getStatus` |
| `QueryController` | `POST /api/confluence/query` | `QueryService.query` |
| `GlobalExceptionHandler` | — | `@RestControllerAdvice` for validation/errors |

### HTTP status mapping (`IngestionController`)

| `IngestionResponse.status()` | HTTP |
|------------------------------|------|
| `ACCEPTED` | 202 |
| `ALREADY_RUNNING` | 409 |
| `ERROR` | 400 |
| default | 200 |

## DTOs (`api.dto`)

| Class | Purpose |
|-------|---------|
| `IngestionRequest` | Body for ingest POST; flags: `forceRebuildManifest`, `extractMarkdown`, `chunkMarkdown`, `ingestVectors`; optional `batchSize`, `concurrency`, `requestTimeoutSeconds` |
| `IngestionResponse` | `status`, `mode`, counts, `manifestPath`, message |
| `IngestionStatus` | Status GET: manifest summary + crawl/batch progress + `activeJob` |
| `IngestionMode` | `MANIFEST_INIT`, `MANIFEST_CRAWL`, `PAGE_TRANSFORM`, `CHUNK`, `VECTOR_INGEST` |
| `IngestionJobStatus` | `ACCEPTED`, `ALREADY_RUNNING`, `ERROR`, `OK` |
| `QueryRequest` | `query`, optional `parentPageId`, `topK`, `similarityThreshold` |
| `QueryResponse` | hits + status |
| `QueryHit` | `webUrl`, `title`, `headingPath`, `text`, `score`, metadata |
| `QueryStatus` | `SUCCESS`, `ERROR`, `DISABLED` |

## Common questions

**Q: What flags start background work?**  
Any of `forceRebuildManifest`, `extractMarkdown`, `chunkMarkdown`, `ingestVectors` → `202 ACCEPTED`.

**Q: Does status need PAT?**  
No. `GET .../status/{parentPageId}` reads local disk only.

**Q: Where is Swagger / OpenAPI?**  
See [observability.md](observability.md#swagger--openapi): UI at `/swagger-ui.html`, spec at `/api-docs`.

**Q: How do I debug a failed ingestion?**  
See [observability.md](observability.md) — logs (`logs/app.log`), `traceId`, progress JSON files, manifest `lastError`.

## Tests

Phase integration tests hit these endpoints via `MockMvc` / REST:  
`src/test/java/com/confluence/ingestor/IngestionPhase*IntegrationTest.java`

## Future module

`query-api/` (query half) + `ingestion-orchestrator/` (ingest half).
