# Package: `com.confluence.ingestor.service`

Parent index: [AGENTS.md](../../AGENTS.md)

Application orchestration — ingestion pipeline, batch execution, query facade.

## Orchestration

| Class | Role |
|-------|------|
| `IngestionService` | **Main facade** — validates request, routes by flags, publishes jobs, builds status |
| `IngestionJobCoordinator` | In-memory mutex per `parentPageId` per stage; implements `JobCoordinatorPort` |
| `IngestionActiveJob` | DTO for active job in status response |

## Stage services (single page / unit of work)

| Class | Role |
|-------|------|
| `ManifestCrawlService` | Async crawl → manifest rebuild → optional chain transform |
| `PageTransformService` | One page: fetch HTML, transform, download assets, write files |
| `ChunkService` | One page: read markdown → chunk → write JSONL |
| `VectorIngestService` | One page: read chunks → embed → Chroma upsert |
| `AttachmentDownloadService` | Image attachment download to `assets/` |
| `TableArtifactService` | Write table JSON to `assets/tables/` |
| `DrawioArtifactService` | Write diagram files to `assets/diagrams/` |
| `QueryService` | Semantic search facade over Chroma + manifest enrichment |

## Batch services (`@Async` on `ingestionTaskExecutor`)

| Class | Chains to on success |
|-------|---------------------|
| `PageTransformBatchService` | `ChunkBatchService` if `chunkMarkdown` in request |
| `ChunkBatchService` | `VectorIngestBatchService` if `ingestVectors` |
| `VectorIngestBatchService` | (end) |

All batches:
- Use virtual-thread executor + semaphore for Confluence HTTP parallelism
- Update `batch-progress.json`
- Increment `noOfRetries` on failure; skip at threshold
- Release `IngestionJobCoordinator` lock in `finally`

## `IngestionService.startIngestion` priority

1. `forceRebuildManifest` → `MANIFEST_CRAWL`
2. `extractMarkdown` → `PAGE_TRANSFORM`
3. `chunkMarkdown` → `CHUNK`
4. `ingestVectors` → `VECTOR_INGEST`
5. else → init or summarize existing manifest

Each background path: `jobCoordinator.tryAcquire*` → `jobPublisher.publish` → `202 ACCEPTED`  
Conflict → `409 ALREADY_RUNNING`

## `PageTransformService` flow (single page)

```
ConfluenceClient.getPageContent
  → HtmlToMarkdownService
  → AttachmentDownloadService (images)
  → TableArtifactService
  → DrawioArtifactService
  → PageStorageService.write
  → ManifestService.updatePageEntry
```

## Dependencies

Pulls from: `confluence`, `transform`, `storage`, `rag`, `config`, `port`, `messaging`

## Tests

Covered indirectly by `IngestionPhase1IntegrationTest` through `IngestionPhase10IntegrationTest`.

## Future module

`ingestion-orchestrator/` (ingest services) + `query-api/` (`QueryService`).
