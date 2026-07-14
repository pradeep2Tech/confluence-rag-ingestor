# Package: `com.confluence.ingestor.model`

Parent index: [AGENTS.md](../../AGENTS.md)

Domain models — Jackson-serialized JSON on disk and in API responses.

## Classes

| Class | File / usage |
|-------|--------------|
| `PageManifest` | Root of `manifest.json` — `baseUrl`, `parentPageId`, `pages[]`, version, timestamps |
| `PageManifestEntry` | One page row — ingestion flags, paths, `noOfRetries`, `lastError` |
| `PageIngestionState` | Optional per-page state document |
| `PageDocument` | `metadata.json` for a transformed page |
| `PageAssetDocument` | Image asset metadata |
| `PageTableDocument` | Complex table JSON in `assets/tables/` |
| `PageDiagramDocument` | Draw.io artifact metadata |
| `ChunkDocument` | One RAG chunk — text, heading path, index, metadata map |

## `PageManifestEntry` ingestion flags

| Field | Meaning |
|-------|---------|
| `markdownExtracted` | `<pageId>.md` written |
| `markdownPath`, `metadataPath`, `assetsDirectory` | Relative paths |
| `chunked` | JSONL chunks written |
| `chunksPath` | Path to `{pageId}.jsonl` |
| `vectorIngested` | Upserted to Chroma |
| `vectorCollection` | Collection name used |
| `noOfRetries` | Incremented on batch failure |
| `lastError` | Last error message |

## State preservation

`PageManifestEntry.copyIngestionState(source, target)` — used on manifest rebuild when page still exists in tree.

## Pending page rules (via `DefaultManifestPolicy`)

| Stage | Pending when |
|-------|--------------|
| Transform | `!markdownExtracted && noOfRetries < threshold` |
| Chunk | `markdownExtracted && !chunked && noOfRetries < threshold` |
| Vector | `chunked && !vectorIngested && noOfRetries < threshold` |

## Future module

`ingestion-contracts/` — staged copy of all model classes.
