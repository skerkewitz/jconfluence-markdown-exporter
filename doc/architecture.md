# Architecture

`jcme` is a thin pipeline: **CLI → resolve URLs → fetch from Confluence → render
markdown → write to disk**, with a JSON lockfile to skip unchanged work between
runs.

## Component map

```
┌────────────────────────────────────────────────────────────────────┐
│  picocli                                                           │
│  ┌──────────────┐ ┌─────────────────────┐ ┌──────────┐ ┌────────┐  │
│  │ PagesCommand │ │ PagesWithDescendant │ │ Spaces   │ │ Orgs   │  │
│  └──────┬───────┘ └─────────┬───────────┘ └────┬─────┘ └───┬────┘  │
│         └─────────┬─────────┴──────────────────┴───────────┘       │
│                   ▼                                                │
│            ExportCommandBase ◄────── ProgressUi                    │
└──────────────────┬─────────────────────────────────────────────────┘
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│  ExportService (orchestrator)                                   │
│   ├── ConfluenceFetcher.resolve*FromUrl  (URL → typed model)    │
│   ├── runExport ─► PageExporter ─► PageRenderer                 │
│   │                              ┐                              │
│   │                ParallelExportRunner (virtual-thread-pool)   │
│   └── runCleanup ─► StaleCleanup ─► LockfileManager.removePages │
└─────────────────────────────────────────────────────────────────┘
   │           │           │            │           │
   ▼           ▼           ▼            ▼           ▼
┌──────┐  ┌──────────┐  ┌────────┐  ┌─────────┐  ┌──────────┐
│Config│  │ Fetcher  │  │Markdown│  │Lockfile │  │  FileIO  │
│Store │  │ + cache  │  │  conv  │  │ Manager │  │  (disk)  │
└──┬───┘  └────┬─────┘  └────────┘  └────┬────┘  └──────────┘
   │           │                          │
   ▼           ▼                          │
┌─────────────────────┐                   │
│ ApiClientFactory    │                   │
│ (URL → REST client) │                   │
└──────────┬──────────┘                   │
           ▼                              │
┌─────────────────┐                       │
│ ConfluenceClient│ ──► HttpExecutor ──► java.net.http.HttpClient
│ JiraClient      │                       │
└─────────────────┘                       │
                                          ▼
                                   confluence-lock.json
```

## Data flow for one page export

What happens when you run `jcme pages https://confluence.example.com/...`:

1. **picocli** routes the args to [PagesCommand.call](../src/main/java/de/skerkewitz/jcme/cli/PagesCommand.java),
   which delegates to [ExportCommandBase.call](../src/main/java/de/skerkewitz/jcme/cli/ExportCommandBase.java).
2. The base class constructs a [ConfigStore](../src/main/java/de/skerkewitz/jcme/config/ConfigStore.java)
   (reads the JSON config + applies `JCME_*` env-var overrides), a TTY-detected
   [ProgressUi](../src/main/java/de/skerkewitz/jcme/cli/progress/ProgressUi.java),
   and an [ExportService](../src/main/java/de/skerkewitz/jcme/export/ExportService.java).
3. **`ExportService.exportPages(urls)`** iterates the URLs and calls
   `fetcher.resolvePageFromUrl(url)` for each. That parses the URL (Cloud /
   Server / gateway / split-host — see [connector.md](connector.md)) and fetches
   the full Page record via the REST API. Pages are cached, so subsequent links
   between pages re-use them.
4. After resolving, the service hands the list to **`runExport`**, which:
   1. Marks every page as "seen" in the lockfile (so they're excluded from
      stale-cleanup).
   2. Filters out pages whose lockfile entry says they're unchanged
      (see [lockfile.md](lockfile.md)).
   3. Submits the survivors to a fixed-size thread pool via
      [ParallelExportRunner](../src/main/java/de/skerkewitz/jcme/export/ParallelExportRunner.java).
5. Each worker:
   1. Re-fetches the Page via the cached fetcher (cheap on second call).
   2. Calls `PageExporter.exportPage(page)`, which:
      - Filters attachments down to those referenced by the page body.
      - Downloads each attachment to disk (skipping unchanged ones via the
        lockfile).
      - Runs [PageRenderer.render(page)](../src/main/java/de/skerkewitz/jcme/markdown/PageRenderer.java)
        to produce the markdown string.
      - Writes the file via atomic temp+rename.
   3. Records the page in the lockfile (page version + attachment versions).
   4. Reports progress to `ProgressUi`.
6. After all pages are done, **`runCleanup`** asks the API which lockfile pages
   no longer exist on the server, and deletes their local files. See
   [lockfile.md](lockfile.md).
7. ProgressUi prints the summary panel. Process exits with code `0` (or `1` if
   any page failed).

## Layered design

| Layer | Knows about | Doesn't know about |
| --- | --- | --- |
| `cli/` | picocli, argument parsing, ExportService | HTTP, JSON, markdown |
| `cli/progress/` | terminal capabilities, stats | export internals |
| `cli/config/` | InteractiveMenu, ConfigStore | export, REST |
| `export/` | ExportService, PageExporter, lockfile, fetcher | HTTP details, markdown internals |
| `markdown/` | jsoup, conversion rules, RenderingContext | HTTP, lockfile, files |
| `fetch/` | ApiClientFactory, JSON → records, caches | markdown, files |
| `api/` | HTTP, auth, URL parsing, retries | records, markdown, files |
| `lockfile/` | JSON lockfile schema, atomic save | HTTP, markdown |
| `model/` | Pure data records | everything else |
| `config/` | JSON config schema, env-var overlay | everything else |
| `utils/` | Drawio mermaid extraction | everything else |

The dependencies flow downward only. No layer above (closer to the CLI) is
referenced from a layer below. This is enforced by package structure, not by
modules.

## Concurrency model

- One `Executors.newFixedThreadPool(maxWorkers)` per export run, started inside
  [ParallelExportRunner](../src/main/java/de/skerkewitz/jcme/export/ParallelExportRunner.java).
- Default `connection_config.max_workers = 20`. Set to `1` (or set
  `export.log_level=DEBUG`) to force serial mode for debugging.
- All caches in [ConfluenceFetcher](../src/main/java/de/skerkewitz/jcme/fetch/ConfluenceFetcher.java)
  are `ConcurrentHashMap`. The HttpClient is thread-safe by JDK guarantee.
- The `LockfileManager` uses a `ReentrantLock` around save+merge so concurrent
  workers can record their pages without losing entries.
- `ExportStats` uses `LongAdder`s (lock-free under contention).
- `ProgressUi` uses `synchronized` on its public methods so per-page progress
  lines from multiple workers don't tear.

## Configuration model

Three layers, applied in this order (later wins):

1. **Defaults**, hard-coded in the records (e.g. `ExportConfig.defaults()`).
2. **JSON config file** at the OS-specific app dir
   (`%APPDATA%\jconfluence-markdown-exporter\app_data.json` on Windows).
3. **Environment variables** prefixed `JCME_` with `__` as the nested separator,
   e.g. `JCME_EXPORT__LOG_LEVEL=DEBUG` or
   `JCME_CONNECTION_CONFIG__MAX_WORKERS=5`.

`ConfigStore.loadEffective()` returns the merged result. Values from env vars
are **never** persisted — they're applied per-run.

## Error model

- `AuthNotConfiguredException` (extends `RuntimeException`) — surfaces from
  `ApiClientFactory` when no credentials match the URL host. Caught at the CLI
  boundary in `ExportCommandBase.call()`, which opens the interactive auth
  prompt pre-filled with the URL.
- `ApiException` — wraps any non-2xx HTTP response or network error after
  retries. `isNotFound()` is special-cased in `fetchPage` to return a
  `Page.inaccessible(id)` sentinel rather than failing the run.
- Per-page failures inside the parallel runner are caught in
  `ParallelExportRunner.runOne`, counted into `ExportStats.failed`, and don't
  abort the run.
- Fatal errors (config parse failure, etc.) propagate out of `ExportService`
  and are logged + exit-code-1'd in `ExportCommandBase`.
