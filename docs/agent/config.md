# Package: `com.confluence.ingestor.config`

Parent index: [AGENTS.md](../../AGENTS.md)

Spring configuration, typed properties, infrastructure beans.

## Classes

| Class | Role |
|-------|------|
| `IngestorProperties` | `@ConfigurationProperties("confluence.ingestor")` — data dir, batch defaults, SSL, retry, chunk size, vector/query toggles, async pool sizes |
| `KafkaProperties` | `@ConfigurationProperties("confluence.ingestor.kafka")` — `enabled`, topics, bootstrap, consumer group |
| `AsyncConfig` | `@EnableAsync`, `ingestionTaskExecutor` thread pool from `asyncCorePoolSize` / `asyncMaxPoolSize` |
| `KafkaInfrastructureConfig` | Kafka producer/consumer factories when Kafka enabled |
| `TracingConfig` | Micrometer OTel tracing setup |

## `application.yml` keys

### `confluence.ingestor.*`

| Key | Default | Meaning |
|-----|---------|---------|
| `data-directory` | `data` | Root for all ingestion artifacts |
| `default-batch-size` | 100 | Pages per batch wave |
| `default-concurrency` | 1 | Parallel page workers per batch |
| `default-request-timeout-seconds` | 60 | Confluence HTTP timeout |
| `verify-ssl` | false | TLS verification for Confluence |
| `manifest-version` | 1 | Written into manifest.json |
| `retry-skip-threshold` | 2 | Skip page when `noOfRetries >=` this |
| `allowed-image-extensions` | png,jpg,... | Attachment download filter |
| `allowed-drawio-extensions` | drawio,xml | Diagram attachment filter |
| `max-chunk-characters` | 4000 | `MarkdownChunker` max size |
| `vector-ingest-enabled` | true | Skip vector stage when false |
| `chroma-collection-name` | confluence-rag | Chroma collection |
| `query-enabled` | true | Disable query API when false |
| `default-query-top-k` | 5 | Default retrieval count |
| `default-similarity-threshold` | 0.0 | Min similarity (0 = all) |
| `per-page-state-enabled` | false | Per-page `ingestion-state.json` |
| `async-core-pool-size` | 1 | Background executor core |
| `async-max-pool-size` | 2 | Background executor max |

### `app.attachment-analysis.*`

| Key | Default | Meaning |
|-----|---------|---------|
| `enabled` | true | Classify downloaded attachments and write `attachments-manifest.json` |
| `vision-enabled` | false | Use local Ollama vision model for generic images; CPU/RAM intensive |
| `vision-model` | qwen3-vl:8b | Ollama vision model when enabled |
| `timeout` | 30s | Per-image vision call timeout |

### `confluence.ingestor.kafka.*`

| Key | Default |
|-----|---------|
| `enabled` | **false** |
| `bootstrap-servers` | localhost:9092 |
| `consumer-group-id` | confluence-ingestor |
| `manifest-crawl-topic` | ingestion.manifest.crawl |
| `page-transform-topic` | ingestion.page.transform |
| `chunk-topic` | ingestion.page.chunk |
| `vector-ingest-topic` | ingestion.page.vector |

### `spring.ai.*`

| Key | Default |
|-----|---------|
| `ollama.base-url` | http://localhost:11434 |
| `ollama.embedding.model` | nomic-embed-text |
| `vectorstore.chroma.client.host/port` | localhost:8000 |
| `vectorstore.chroma.collection-name` | `${confluence.ingestor.chroma-collection-name}` |

### Observability

- Actuator: `health`, `info`, `metrics`, `prometheus`
- OTLP traces: `http://localhost:4318/v1/traces`
- Log pattern includes `traceId`, `spanId`
- Log file: `logs/app.log`

Full logging, tracing, and Swagger guide: [observability.md](observability.md).

## Common questions

**Q: How to run without Kafka?**  
Default — `kafka.enabled=false` activates `InProcessJobPublisher`.

**Q: How to reduce Windows resource pressure?**  
Keep `default-concurrency` at 1, keep `app.attachment-analysis.vision-enabled=false`, and enable vision only for small test batches on a machine that can comfortably run the selected Ollama vision model.

**Q: Local profile?**  
Check for `application-local.yml` in `src/main/resources/`.

## Future module

`ingestion-orchestrator/` — all config classes staged there.
