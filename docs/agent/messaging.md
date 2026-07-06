# Package: `com.confluence.ingestor.messaging`

Parent index: [AGENTS.md](../../AGENTS.md)

Job dispatch — in-process (default) or Kafka.

## Classes

| Class | Role |
|-------|------|
| `IngestionJobType` | `MANIFEST_CRAWL`, `PAGE_TRANSFORM`, `CHUNK`, `VECTOR_INGEST` |
| `IngestionJobMessage` | Kafka payload wrapper around job type + request |
| `IngestionTopics` | Topic + DLQ name constants |
| `InProcessJobPublisher` | **Default** — calls `@Async` batch services directly |
| `KafkaJobPublisher` | Publishes to Kafka when `kafka.enabled=true` |
| `KafkaIngestionJobConsumers` | Consumes topics, invokes same batch services |

## Topic names

| Job type | Topic | DLQ |
|----------|-------|-----|
| MANIFEST_CRAWL | `ingestion.manifest.crawl` | `.dlq` suffix |
| PAGE_TRANSFORM | `ingestion.page.transform` | `.dlq` |
| CHUNK | `ingestion.page.chunk` | `.dlq` |
| VECTOR_INGEST | `ingestion.page.vector` | `.dlq` |

Configurable via `confluence.ingestor.kafka.*-topic` properties.

## Activation

| `kafka.enabled` | Active publisher |
|-----------------|------------------|
| `false` (default) | `InProcessJobPublisher` |
| `true` | `KafkaJobPublisher` + consumers |

`@ConditionalOnProperty` on publisher beans.

## `InProcessJobPublisher` dispatch

```
MANIFEST_CRAWL  → ManifestCrawlService.runManifestCrawlAsync
PAGE_TRANSFORM  → PageTransformBatchService.runPageTransformBatchAsync
CHUNK           → ChunkBatchService.runChunkBatchAsync
VECTOR_INGEST   → VectorIngestBatchService.runVectorIngestBatchAsync
```

## Who publishes

`IngestionService` calls `jobPublisher.publish(...)` after acquiring job coordinator lock.

## Tests

`KafkaJobPublisherIntegrationTest.java` — Spring Kafka test support.

## Future module

`ingestion-orchestrator/` — messaging adapters staged there.  
Contracts (`IngestionJobMessage`, topics) in `ingestion-contracts/`.
