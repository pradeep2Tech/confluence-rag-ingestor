# Package: `com.confluence.ingestor.transform`

Parent index: [AGENTS.md](../../AGENTS.md)

Pure HTML→Markdown transformation. No storage I/O, no HTTP.

## Classes

| Class | Role |
|-------|------|
| `HtmlToMarkdownService` | Main entry: Confluence storage XHTML → Markdown |
| `TableExtractor` | Complex tables → JSON + `[TABLE:id](path)` placeholder |
| `DrawioExtractor` | Draw.io XML → label text + diagram files |
| `StorageImageReferenceExtractor` | Finds `ac:image` / `ri:attachment` refs in storage XML |
| `StorageDrawioReferenceExtractor` | Finds draw.io macro references |
| `ExtractedTable` | Table extraction result DTO |
| `ExtractedDiagram` | Diagram extraction result DTO |
| `DrawioReference` | Parsed draw.io attachment reference |

## Transform pipeline (conceptual)

```
body.storage HTML
  → JSoup parse
  → extract images (placeholders for download later)
  → extract complex tables → assets/tables/*.json
  → extract draw.io → assets/diagrams/
  → convert remaining HTML → Markdown
```

## Called by

- `PageTransformService` (orchestrates transform + artifact services)
- `TableArtifactService`, `DrawioArtifactService` use extractors

## Config dependencies

- Image extensions: `confluence.ingestor.allowed-image-extensions`
- Draw.io extensions: `confluence.ingestor.allowed-drawio-extensions`

## Tests

| Test | Covers |
|------|--------|
| `HtmlToMarkdownServiceTest` | Core conversion |
| `TableExtractorTest` | Merged/nested cells |
| `DrawioExtractorTest` | Diagram text |
| `StorageImageReferenceExtractorTest` | Image refs |
| `StorageDrawioReferenceExtractorTest` | Macro refs |

## Future module

`content-transform/` — staged copy.
