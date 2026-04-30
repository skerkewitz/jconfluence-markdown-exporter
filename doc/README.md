# Documentation

How `jcme` works internally, written for someone who needs to understand or modify
it. For end-user docs (install, configure, run) see the top-level
[README](../README.md).

## Read in this order

1. **[architecture.md](architecture.md)** — component map, data flow from CLI
   invocation to written-file. Start here.
2. **[connector.md](connector.md)** — the REST/auth layer: how an HTTP request
   is built, retried, and routed (especially the split-host setup at AXA-style
   corporate Confluence installs).
3. **[crawling.md](crawling.md)** — how each of the four export modes (`pages`,
   `pages-with-descendants`, `spaces`, `orgs`) discovers and queues pages.
4. **[conversion.md](conversion.md)** — the big tag-by-tag table of how
   Confluence HTML maps to Markdown, including every macro we recognize.
5. **[lockfile.md](lockfile.md)** — how skip-unchanged and stale-cleanup work
   between runs.
6. **[limitations.md](limitations.md)** — what's known broken or missing, and
   where the next maintainer should start.

## Conventions in these docs

- Code references look like [Page.java](../src/main/java/de/skerkewitz/jcme/model/Page.java)
  and click through to the source.
- "Confluence" without qualifier means "the REST API of a Confluence Server,
  Data Center, or Cloud instance". Differences are called out where they matter.
- "MD" is markdown, "PR" is pull request, "DC" is Data Center.

## Where to look in the code

```
src/main/java/de/skerkewitz/jcme/
├── App.java                  picocli root command + Logging.initFromConfig() call
├── Logging.java              applies export.log_level to Logback at startup
├── api/                      REST client, URL parsing, auth lookup
├── cli/                      picocli subcommands
│   ├── config/               config get/set/list/edit + interactive menu
│   └── progress/             ProgressUi (TTY + plain modes)
├── config/                   AppConfig records + ConfigStore (load/save/env-overlay)
├── export/                   PageExporter, ParallelExportRunner, StaleCleanup, ExportService
├── fetch/                    ConfluenceFetcher (typed REST -> records)
├── lockfile/                 ConfluenceLock + LockfileManager
├── markdown/                 HTML -> MD converter, page renderer
├── model/                    Page, Space, Attachment, Version, ...
└── utils/                    DrawioMermaid extraction
```
