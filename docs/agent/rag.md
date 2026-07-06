# Package: `com.confluence.ingestor.rag`

Parent index: [AGENTS.md](../../AGENTS.md)

Chunking, embeddings, Chroma vector store ingest and query.

## Classes

| Class | Role |
|-------|------|
| `MarkdownChunker` | Heading-scoped split; max chars from config |
| `EmbeddingService` | Ollama embedding via Spring AI |
| `ChromaIngestionService` | Upsert `ChunkDocument` → Chroma collection |
| `ChromaQueryService` | Similarity search over collection |
| `VectorStorePortAdapter` | Implements `VectorStorePort` wrapping Chroma + embeddings |

## Chunking (`MarkdownChunker`)

- Splits on `#` headings (levels 1–6)
- `headingPath` = `H1 > H2 > ...` separator
- Oversized sections split by character budget (`max-chunk-characters`)
- Output: `List<ChunkDocument>` with metadata (pageId, parentPageId, title, etc.)

## Ingest path

`VectorIngestService` → reads JSONL chunks → `ChromaIngestionService.ingestChunks`  
Uses Spring AI `VectorStore` + Ollama `EmbeddingModel`.

## Query path

`QueryService` → `ChromaQueryService.search` → maps `Document` metadata to `QueryHit`  
Enriches with `webUrl`/`title` from `manifest.json` when `parentPageId` scoped.

## External dependencies

| System | Config |
|--------|--------|
| Ollama | `spring.ai.ollama.*` — model `nomic-embed-text` |
| ChromaDB | `spring.ai.vectorstore.chroma.*` — default port 8000 |
| Collection | `confluence.ingestor.chroma-collection-name` |

## Gating

- Ingest: `confluence.ingestor.vector-ingest-enabled`
- Query: `confluence.ingestor.query-enabled`

## Tests

| Test | Covers |
|------|--------|
| `MarkdownChunkerTest` | Heading splits, size limits |
| `ChromaIngestionServiceTest` | Ingest with test AI config |
| `ChromaQueryServiceTest` | Search scoring |

Test AI wiring: `support/TestAiConfiguration.java`

## Future module

`embedding-indexer/` — staged copy.
