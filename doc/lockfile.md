# Lockfile, skip-unchanged, and stale cleanup

A small JSON file under `output_path` that records what's been exported, so
re-runs can skip unchanged pages and clean up files for pages that have
been deleted from Confluence.

Source: [LockfileManager.java](../src/main/java/de/skerkewitz/jcme/lockfile/LockfileManager.java),
[ConfluenceLock.java](../src/main/java/de/skerkewitz/jcme/lockfile/ConfluenceLock.java),
[StaleCleanup.java](../src/main/java/de/skerkewitz/jcme/export/StaleCleanup.java).

## Where it lives

```
<export.output_path>/<export.lockfile_name>
```

Defaults to `<output_path>/confluence-lock.json`. Configurable via:

```sh
jcme config set export.lockfile_name=my-lock.json
jcme config set export.skip_unchanged=false   # disable entirely
```

## Schema

`ConfluenceLock` is a tree:

```
ConfluenceLock
├── lockfile_version: int (currently 2)
├── last_export: string (UTC ISO-8601)
└── orgs: Map<String /* base URL */, OrgEntry>
    └── spaces: Map<String /* space key */, SpaceEntry>
        └── pages: Map<String /* page id */, PageEntry>
            ├── title: string
            ├── version: int
            ├── export_path: string (relative to output_path)
            └── attachments: Map<String /* attachment id */, AttachmentEntry>
                ├── version: int
                └── path: string (relative to output_path)
```

The page ID is the universal key — under each org/space, pages are looked
up by their numeric Confluence ID, not their title.

A typical `confluence-lock.json` looks like:

```json
{
  "lockfile_version": 2,
  "last_export": "2026-01-15T14:30:00Z",
  "orgs": {
    "https://wiki.example.com/confluence": {
      "spaces": {
        "DOCS": {
          "pages": {
            "100": {
              "title": "Hello",
              "version": 5,
              "export_path": "DOCS/Hello.md",
              "attachments": {
                "att1": { "version": 1, "path": "DOCS/attachments/guid-1.png" }
              }
            }
          }
        }
      }
    }
  }
}
```

## Format upgrades

`load(file)` checks `lockfile_version` against the constant
`ConfluenceLock.LOCKFILE_VERSION` (currently 2). If the on-disk version is
older, the loader logs an INFO message and returns a fresh empty lock.
That triggers a full re-export on the next run, but no data is lost — only
the lockfile starts over.

If a future schema change needs a non-breaking migration instead, add a
version-aware reader to `ConfluenceLock.load`.

## Atomic writes

Every save goes through:

1. Serialize the merged lock to JSON bytes.
2. Write to a sibling `<lockfile>.tmp` file via `FileIO.write`.
3. `Files.move(tmp, real, REPLACE_EXISTING, ATOMIC_MOVE)` (with a non-atomic
   fallback when ATOMIC_MOVE fails — Windows under aggressive antivirus
   sometimes blocks it).

The merge step protects against concurrent writes from parallel workers:
before writing, the manager re-reads the on-disk lockfile and merges any
entries that landed between our read and our save. Combined with a
`ReentrantLock` around the save itself, this means parallel `recordPage`
calls don't lose entries.

## Lifecycle of one export run

`LockfileManager` is one instance per export run. The flow:

```
LockfileManager mgr = new LockfileManager(outputRoot, lockfileName, enabled);
   │
   ▼
mgr.markSeen(targets);        // record IDs as "encountered this run"
   │
   ▼
for each target:
    if mgr.shouldExport(target, pendingPath):
        toExport.add(target)
    else:
        stats.skipped++
   │
   ▼
ParallelExportRunner runs each survivor through PageExporter.
After each successful export:
    mgr.recordPage(page, attachmentEntries, rc);   // updates lockfile, atomic save
   │
   ▼
At end of run, ExportService.runCleanup:
    unseen = mgr.unseenIds()                       // pages in lockfile NOT seen this run
    deleted = staleCleanup.fetchDeletedPageIds(unseen, baseUrl)
    mgr.removePages(deleted, stats);               // delete files + lockfile entries
```

All three sets — `seen`, `unseen`, `deleted` — work with stringified Confluence
page IDs.

## Skip-unchanged decision

`shouldExport(page, resolvedExportPath)` returns true when **any** of these
holds:

1. `lockfile.skip_unchanged` is `false` (everything always re-exports).
2. The lockfile has no entry for this page id.
3. The page's `version` is null (we can't compare).
4. The local file at the recorded `export_path` no longer exists on disk.
5. The page's current version doesn't match the recorded version.
6. The page would now write to a different `export_path` than recorded
   (e.g. someone changed `export.page_path` template, or the page was
   renamed and the template includes `{page_title}`).

Otherwise it returns false (skip). Skipped pages **don't** get re-fetched —
the discovery phase only loads enough metadata to make this decision (a
`Descendant` record has `id` + `version` + ancestors-derived path, no body).

DEBUG logs explain each decision:

```
DEBUG Page id=100 not in lockfile — will export
DEBUG Page id=100 output file missing — will re-export
DEBUG Page id=100 changed (v3 → v4) — will export
DEBUG Page id=100 unchanged (v3) — skipping
```

## Attachment skip-unchanged

Per-attachment in `PageExporter.exportAttachments`:

```java
AttachmentEntry old = oldEntries.get(attachment.id());
if (old != null && old.version() == attVersion) {
    Path expected = outputRoot.resolve(old.path());
    if (Files.exists(expected)) {
        // Skip — file is current.
        newEntries.put(attId, old);
        stats.incAttachmentsSkipped();
        continue;
    }
}
```

Same three-condition check as for pages: in-lockfile + version-match +
file-exists. Otherwise download.

When an attachment moves to a new path (e.g. file ID changed because the
attachment was deleted and re-uploaded), the old file is reaped:

```java
for (Map.Entry<String, AttachmentEntry> entry : oldEntries.entrySet()) {
    AttachmentEntry now = newEntries.get(entry.getKey());
    if (now != null && !entry.getValue().path().equals(now.path())) {
        FileIO.deleteIfExists(outputRoot.resolve(entry.getValue().path()));
        stats.incAttachmentsRemoved();
    }
}
```

## Page move detection

If a page's title changes (or the path template changes) and the new
`export_path` differs from the recorded one, `shouldExport` returns true
and the worker writes the markdown to the new location. After the run,
`removePages` (which is also called for moved pages, not just deleted ones)
deletes the old file:

```java
for (String pageId : seenPageIds) {
    PageEntry old = snapshot.get(pageId);
    PageEntry now = lock.getPage(pageId);
    if (old != null && now != null && !old.exportPath().equals(now.exportPath())) {
        FileIO.deleteIfExists(outputPath.resolve(old.exportPath()));
        LOG.info("Deleted old path for moved page: {}", old.exportPath());
    }
}
```

The `_all_entries_snapshot` field captures the pre-run lockfile state so we
can compare old vs new paths even though the live lockfile gets mutated by
parallel workers during the run.

## Stale cleanup

After all pages have exported, `ExportService.runCleanup` asks the API
which lockfile pages no longer exist on the server.

**Per-base-URL**, in case you exported pages from multiple Confluence
instances in different runs (we don't want this run's cleanup to remove
unrelated entries from another server):

```java
for (String baseUrl : seenBaseUrls) {
    Set<String> unseen = lockfile.unseenIds();   // not encountered this run
    if (unseen.isEmpty()) continue;
    Set<String> deleted = cleanup.fetchDeletedPageIds(sorted(unseen), baseUrl);
    lockfile.removePages(deleted, stats);
}
```

`StaleCleanup.fetchDeletedPageIds` queries the API in batches:

| API mode | Endpoint | Batch size |
| --- | --- | --- |
| `connection_config.use_v2_api=true` | `GET /api/v2/pages?id=A&id=B&...` | `existence_check_batch_size` (default 250) |
| Otherwise (v1 CQL) | `GET /rest/api/content/search?cql=id in (A,B,...)` | `min(batch_size, 25)` (CQL hard limit) |

Returns the set of IDs the API confirms as **gone**. IDs the API still
acknowledges are kept in the lockfile.

If a batch fails (e.g. network error), all IDs in that batch are assumed
to **still exist** — better to leak a stale lockfile entry than to
incorrectly delete a user's exported file.

`removePages(deletedIds, stats)`:

1. For each deleted page, delete the local file at its recorded path.
2. Remove the page entry from the lockfile.
3. Atomically save the updated lockfile.
4. Increment `stats.removed` for each deletion.

## Disabling

```sh
jcme config set export.skip_unchanged=false   # always re-export everything
jcme config set export.cleanup_stale=false    # never delete files
```

When `skip_unchanged=false`, `LockfileManager` is still constructed but
`shouldExport` always returns true. Pages still get recorded so the
attachment skip-unchanged works on the next run, and so cleanup still has
something to compare against.

When `cleanup_stale=false`, `runCleanup` short-circuits at the start. No
API existence checks, no file deletions.

## Cache vs lockfile vs disk

| Layer | Lifetime | Purpose |
| --- | --- | --- |
| `ConfluenceFetcher` caches | One run | Avoid re-fetching the same Page/Space/User during this run |
| Lockfile | Across runs | Skip work for unchanged pages and detect deletions |
| Disk files | Persistent | The actual exported markdown |

The lockfile is **not** a cache of page content — it only holds metadata
needed to decide whether to re-export. The on-disk markdown files are the
source of truth for "what the user has". If you delete the markdown files
but keep the lockfile, the next run will re-export everything (the
"local file missing" condition).

If you delete the lockfile but keep the markdown files, the next run will
also re-export everything (the "page not in lockfile" condition). The
markdown files will be rewritten in place. No data loss — just wasted
download bandwidth.
