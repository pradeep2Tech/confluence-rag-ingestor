# Package: `com.confluence.ingestor.storage`

Parent index: [AGENTS.md](../../AGENTS.md)

On-disk persistence — manifests, progress, pages, chunks. Implements `ManifestRepository`.

## Classes

| Class | Role |
|-------|------|
| `FileStorageService` | Path resolution under `data/{parentPageId}/`, atomic JSON writes, Windows retry |
| `ManifestService` | Thread-safe manifest CRUD; implements `ManifestRepository` |
| `ManifestLockCoordinator` | Per-`parentPageId` `ReentrantLock` for manifest updates |
| `DefaultManifestPolicy` | Retry/skip rules, pending page selection; implements `ManifestPolicy` |
| `CrawlProgressService` | `crawl-progress.json` read/write |
| `BatchProgressService` | `batch-progress.json` for batch phases |
| `BatchPhase` | Enum: `PAGE_TRANSFORM`, `CHUNK`, `VECTOR_INGEST` |
| `PageStorageService` | `page.md`, `metadata.json`, assets paths |
| `ChunkStorageService` | `chunks/{pageId}.jsonl` read/write |
| `PageIngestionStateService` | Optional per-page state files when enabled |

## Key paths (`FileStorageService`)

| Method | Path |
|--------|------|
| `manifestPath` | `data/{parentPageId}/manifest.json` |
| `crawlProgressPath` | `.../crawl-progress.json` |
| `batchProgressPath` | `.../batch-progress.json` |
| `pageDir` | `.../pages/{pageId}/` |
| `chunksDir` | `.../chunks/` |

Atomic write: temp file + replace with up to 15 retries (`ATOMIC_REPLACE_MAX_ATTEMPTS`).

## `ManifestService` highlights

- `createEmptyManifestIfMissing`
- `rebuildManifestFromCrawl` — merges ingestion state from old manifest for surviving pages
- `updatePageEntry` — locked read-modify-write
- `loadManifest` / `saveManifest`
- Implements `ManifestRepository` + `ManifestSummary` counts

## `batch-progress.json` fields

`phase`, `status` (RUNNING/COMPLETED/FAILED), `processedCount`, `failedCount`, `totalPending`, `currentPageId`, `startedAt`, `error`

## Called by

All `service/*BatchService`, `IngestionService.getStatus`, `QueryService` (manifest for web URLs)

## Tests

- `ManifestServiceMergeTest.java` — rebuild preserves ingestion state

## Future module

`ingestion-state/` — staged copy.
