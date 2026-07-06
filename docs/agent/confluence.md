# Package: `com.confluence.ingestor.confluence`

Parent index: [AGENTS.md](../../AGENTS.md)

Confluence REST I/O — client, crawler, attachments. Implements `ConfluencePort`.

## Classes

| Class | Role |
|-------|------|
| `ConfluenceClient` | HTTP client; Bearer PAT; implements `ConfluencePort` |
| `ConfluenceClientFactory` | Creates per-request clients (baseUrl, pat, timeout, SSL) |
| `ConfluenceClientError` | Typed client failures |
| `PageCrawler` | DFS descendant discovery via repeated `GET .../child/page` |
| `AttachmentClient` | Lists/downloads page attachments |

## REST endpoints used

| Operation | Endpoint |
|-----------|----------|
| Page content | `GET /rest/api/content/{pageId}?expand=body.storage,body.view,version,space,ancestors` |
| Child pages | `GET /rest/api/content/{pageId}/child/page` (paginated) |
| Attachments | `GET /rest/api/content/{pageId}/child/attachment` |

**Not used:** `/descendant/page` (501 on many DC builds).

## DTOs (`confluence.dto`)

`ConfluencePageDto`, `ConfluencePageContentDto`, `ConfluencePageBody`, `ConfluenceStorageBody`,  
`ConfluenceChildListResponse`, `ConfluenceAttachmentDto`, `ConfluenceAttachmentListResponse`,  
`ConfluenceAncestor`, `ConfluenceSpace`, `ConfluenceVersion`

## `ConfluencePort` methods

- `getContent(pageId, expand)`
- `getPageContent(pageId)` — storage HTML for transform
- `listDirectChildren` / `iterateDirectChildren`
- `buildWebUrl(page)` — source link for RAG hits

## Crawl behavior

`PageCrawler.crawl(client, rootPageId, callback)`:
- Stack-based depth-first walk
- Progress callback → `CrawlProgressService` (via `ManifestCrawlService`)
- Returns ordered `List<ConfluencePageDto>`

## Auth & security

- PAT passed as `Authorization: Bearer {pat}`
- **Never log PAT** — factory/client hold it in memory only

## Tests

- `ConfluenceClientTest.java` — MockWebServer
- `PageCrawlerTest.java`
- `AttachmentClientTest.java`

## Future module

`confluence-connector/` — staged copy of this package.
