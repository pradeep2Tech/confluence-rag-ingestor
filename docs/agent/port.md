# Package: `com.confluence.ingestor.port`

Parent index: [AGENTS.md](../../AGENTS.md)

Hexagonal ports — abstractions for testability and future microservice boundaries.

## Interfaces

| Port | Purpose | Default implementation |
|------|---------|------------------------|
| `ConfluencePort` | Confluence REST read API | `ConfluenceClient` |
| `ManifestRepository` | Manifest load/save/update | `ManifestService` |
| `ManifestPolicy` | Pending pages, retry rules | `DefaultManifestPolicy` |
| `ManifestSummary` | Record: total/ingested/pending/failed counts | returned by `ManifestService` |
| `VectorStorePort` | Embed + upsert + similarity search | `VectorStorePortAdapter` |
| `JobPublisher` | Dispatch pipeline stage jobs | `InProcessJobPublisher` or `KafkaJobPublisher` |
| `JobCoordinatorPort` | Per-parent job mutex | `IngestionJobCoordinator` |

## `ConfluencePort` (see confluence.md)

`baseUrl`, `getContent`, `getPageContent`, `listDirectChildren`, `iterateDirectChildren`, `buildWebUrl`

## `JobPublisher`

```java
void publish(IngestionJobType jobType, IngestionRequest request);
```

## `JobCoordinatorPort`

Acquire/release per stage: `tryAcquireManifestBuild`, `tryAcquirePageTransform`, `tryAcquireChunkBatch`, `tryAcquireVectorIngest` (+ releases).

## `VectorStorePort`

Abstraction over Chroma + embeddings — used by `VectorIngestService` and query path.

## Design rule

New cross-package dependencies should target these interfaces, not concrete `service` or `storage` classes.

## Observability

Tracing is not a separate port — see [observability.md](observability.md) for `TracingConfig`, `@Observed`, and OTLP export.

## Future module

`ingestion-contracts/` — all port interfaces staged there.
