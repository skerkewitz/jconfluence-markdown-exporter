# Crawling

How `jcme` discovers which pages to export, for each of the four export
modes. All four go through `ExportService.exportPages` /
`exportPagesWithDescendants` / `exportSpaces` / `exportOrganizations` in
[ExportService.java](../src/main/java/de/skerkewitz/jcme/export/ExportService.java).

## Two-phase model

Every export run has the same shape:

1. **Discovery phase** — turn user input (URLs) into a flat
   `List<ExportablePage>` (where `ExportablePage` is a sealed interface
   implemented by `Page` and `Descendant`).
2. **Export phase** — `runExport(...)` mark-seen → filter unchanged →
   ParallelExportRunner.

Discovery is sequential and single-threaded. Export is parallel.

The four modes only differ in **how discovery works**.

## Mode 1: `pages` — explicit list

```sh
jcme pages https://wiki.example.com/display/K/PageA https://wiki.example.com/display/K/PageB
```

For each URL:

1. `fetcher.resolvePageFromUrl(url)`:
   - Parse URL → `(baseUrl, ref)`.
   - If the URL has `?pageId=N`, fetch `Page.from_id(N)` directly.
   - Else if the path matched as `(spaceKey, pageId)`, fetch by ID.
   - Else if matched as `(spaceKey, pageTitle)`, call `getPageByTitle(K, T)`
     to resolve the ID, then fetch by ID.
   - Returns a fully-loaded `Page` (body, ancestors, attachments, version).
2. Append to `targets`.
3. Record the page's `baseUrl` in `seenBaseUrls` for the cleanup phase.

No descendants are fetched. The list is exactly the URLs you passed.

## Mode 2: `pages-with-descendants` — page tree

```sh
jcme pages-with-descendants https://wiki.example.com/display/K/RootPage
```

For each URL:

1. `fetcher.resolvePageFromUrl(url)` — same as mode 1, returns a `Page`.
2. `fetcher.getDescendants(page)` — returns `List<Descendant>` (every page
   transitively below the root, regardless of nesting depth).
3. `targets.add(page); targets.addAll(descendants)`.

### How `getDescendants` works

In [ConfluenceFetcher.getDescendants](../src/main/java/de/skerkewitz/jcme/fetch/ConfluenceFetcher.java):

```java
client.searchCql(
    "type=page AND ancestor=" + page.id(),
    "metadata.properties,ancestors,version",
    250  // page size
);
```

Then follows `_links.next` for pagination:

```java
String next = JsonHelpers.text(response.path("_links"), "next");
while (next != null && !next.isEmpty()) {
    response = client.getRelative(next);
    collectDescendants(response, result, page.baseUrl());
    next = JsonHelpers.text(response.path("_links"), "next");
}
```

The CQL `ancestor=N` operator returns **every** transitive descendant — Atlassian
walks the tree server-side. We don't recurse manually.

Each result is parsed into a `Descendant` record with the same
`(baseUrl, id, title, space, ancestors, version)` shape as a `Page`, but
without the body. The body is fetched lazily later by the worker.

### Why `Descendant` instead of `Page`

Discovery only needs enough info to:
- Mark the page as seen in the lockfile (just the ID).
- Filter unchanged pages (just the version).
- Compute the export path (needs space + ancestors).

Loading the full body for every page just to throw most of them away
(skip-unchanged) would waste API quota. So discovery uses lightweight
`Descendant` records, and the worker promotes each to a `Page` via
`fetcher.getPage(id, baseUrl)` only when the page actually needs exporting.

The `Page` cache makes this cheap if the page was already loaded as part of
URL resolution (mode 1) — `getPage` returns the cached instance.

## Mode 3: `spaces` — every page in a space

```sh
jcme spaces https://wiki.example.com/display/K
```

For each URL:

1. `fetcher.resolveSpaceFromUrl(url)` — parse out the space key, fetch
   `getSpace(key, expand=homepage)`.
2. `collectSpacePages(space, targets)`:
   - If `space.homepage == null`, log a warning and skip this space.
   - Else fetch the homepage via `fetcher.getPage(homepage_id, baseUrl)`.
   - Add the homepage to `targets`.
   - Add all of `getDescendants(homepage)` to `targets`.

This relies on Confluence's convention that **every page in a space is a
descendant of the space's homepage**. That's true for spaces created the
normal way, but *not* always true for orphaned pages — see
[limitations.md](limitations.md).

## Mode 4: `orgs` — every space in an instance

```sh
jcme orgs https://wiki.example.com
```

For each base URL:

1. `fetcher.resolveOrganizationFromUrl(baseUrl)` →
   `getOrganization(baseUrl)`, which paginates `/rest/api/space?type=global`
   until the API returns fewer than `limit` results.
   Page size: `100`.
2. For each `Space` in the org, run `collectSpacePages(space, targets)` (same
   as mode 3).

Personal spaces (`type=personal`) are **not** exported by default — only
`type=global`. To export everything, run `spaces` mode against each personal
space URL individually.

## What "seenBaseUrls" is for

Every mode collects a `Set<String>` of base URLs encountered during
discovery. After the export phase, `runCleanup` iterates that set and asks
the API which pages from the lockfile under each base URL no longer exist.
See [lockfile.md](lockfile.md#stale-cleanup) for details.

The point is: if you only export pages from `wiki.example.com` in this run,
you don't want stale-cleanup to remove pages from the lockfile that came
from a *different* server you exported last week. Limiting cleanup to the
base URLs you actually touched keeps multi-server usage safe.

## Filtering before export

After discovery, `runExport` does:

```java
lockfile.markSeen(targets);                   // record IDs as seen-this-run
List<ExportablePage> toExport = new ArrayList<>();
for (ExportablePage target : targets) {
    Path pendingExportPath = pendingExportPath(target, ...);
    if (lockfile.shouldExport(target, pendingExportPath)) {
        toExport.add(target);
    } else {
        stats.incSkipped();
    }
}
```

`lockfile.shouldExport(page, path)` returns `true` when:

1. The page isn't in the lockfile yet, **or**
2. The local file at the recorded path no longer exists, **or**
3. The page version on the API is greater than what's recorded, **or**
4. The export path template would write the page to a different file than
   what's recorded (e.g. someone changed `export.page_path`).

Otherwise the page is skipped (counted as `skipped` in `ExportStats`).

## Parallel export

`ParallelExportRunner.run(toExport, stats)` then submits each survivor to
`Executors.newFixedThreadPool(maxWorkers)`. Each worker:

1. Promotes the `ExportablePage` to a full `Page` via the fetcher.
2. Calls `PageExporter.exportPage(page)` (downloads attachments + writes md).
3. Records the page in the lockfile (page version + attachment versions).
4. Reports to `ProgressUi`.

Worker thread names are `jcme-export-1`, `jcme-export-2`, … — visible in
DEBUG logs and in the per-page progress lines.

In serial mode (`max_workers ≤ 1` or `log_level=DEBUG`) the pool is skipped
and pages run on the calling thread, which makes log output much easier to
follow during debugging.

## Worst-case discovery cost

For the largest mode (`orgs`):

- 1 round-trip per page of `getAllSpaces` (page size 100).
- For each space: 1 round-trip for `getSpace`, 1 for the homepage Page,
  N round-trips for `getDescendants` (paginated, page size 250).
- For each unique space encountered while parsing ancestors: 1 round-trip
  (cached after the first call).

So a 1000-space org with 50 pages each: ~10 (space list) + 1000 × (1 + 1 + 1)
= ~3010 round-trips just to **discover** what to export. The export phase is
on top: ~50,000 page fetches plus attachments.

Set `connection_config.max_workers=20` (default) to spread the export-phase
calls. Discovery itself stays sequential to keep the CQL queries simple.

## Cycle safety

CQL `ancestor=` returns descendants, never ancestors. There's no risk of
infinite loops in discovery itself.

In the markdown converter, a page link could point back to an ancestor.
`PageRenderer.renderBreadcrumbs` and `convertPageLink` only do a single
fetch per target; the `pageCache` ensures we don't re-render anything.
Cycles within a page's body would just mean two markdown links pointing
to each other, which is fine.
