# Limitations and improvement ideas

A frank list of what `jcme` doesn't do, what it does poorly, and where the
next maintainer should start. Some are easy fixes, some are bigger
projects — flagged with rough effort guesses.

## Not yet validated against a real Confluence

The biggest one. The full pipeline (URL parsing, REST client, page fetch,
markdown rendering, file write, lockfile, cleanup) has 270+ unit and
integration tests, **all running against in-process `HttpServer` mocks**.
There has been **no end-to-end validation against a live Confluence
Server, Data Center, or Cloud instance**.

Risks that only show up on real data:
- HTML quirks specific to a Confluence version's rendering (e.g. a macro
  emitting different attribute names than we expect)
- Authentication edge cases on Server with corporate SSO
- Performance on large spaces (10k+ pages, 100k+ attachments)
- Charset weirdness if a server returns Latin-1 JSON

**Effort to validate**: an afternoon if you have credentials. Just run
`jcme pages <url>` against a small page with mixed content (text, images,
tables, a code block, a page link, an attachment) and compare the output
against the rendered HTML.

## Native-image build is configured but never run

[build.gradle.kts](../build.gradle.kts) and [pom.xml](../pom.xml) both wire
in the GraalVM `native-build-tools` plugin with sensible defaults and
reflection metadata for our own Jackson-bound records. The
plugin-contributed Gradle tasks (`nativeCompile`, `nativeRun`,
`collectReachabilityMetadata`) are present.

But: I don't have GraalVM installed in the build environment that wrote
this code, so `nativeCompile` has never actually run end-to-end. There
will almost certainly be missing reflection metadata for at least one of
Jackson YAML, jsoup, or the Java HttpClient that surfaces only when you
try it on a GraalVM JDK. The README's
[GraalVM section](../README.md#graalvm-native-image-true-native-binary)
documents the agent-based regeneration flow for capturing what's needed.

**Effort to validate**: 30 minutes plus iteration. Install GraalVM 21+,
run `./gradlew nativeCompile`, then `./gradlew -Pagent run --args="..."`
to capture any missing metadata, commit the JSON files.

## Phase 7 progress UI is line-per-page only

[ProgressUi](../src/main/java/de/skerkewitz/jcme/cli/progress/ProgressUi.java)
renders a static progress line per completed page (with a `[N/total]`
counter). It does **not**:

- Show an animated progress bar with elapsed/remaining time during the
  parallel export phase.
- Render a live count as pages tick off in real time.

The Python original uses `rich`'s `Progress` columns to do this with
threading. Doing the same in Java requires a renderer thread that holds a
"draw frame" lock with the workers, and clears the previous frame with
`\r\033[K` before each redraw. Implementable in ~150 lines but adds enough
threading complexity that I deferred it.

**Effort**: 1–2 days for a robust live-redraw with proper handling of
concurrent log lines + terminal resizing.

## Interactive menu is plain numbered selections, not arrow-key

The Python tool uses `questionary` for arrow-key-driven menus. We use
plain `1) … 2) … 3)` numbered prompts via
[Prompt.java](../src/main/java/de/skerkewitz/jcme/cli/config/Prompt.java).
Functionally equivalent but visually rougher.

**Why**: arrow-key handling on Windows requires either jline (which I
removed because we weren't using it and it's annoying for native-image) or
JNI/JNA tricks. The numbered approach Just Works everywhere, including
piped/redirected input and CI.

**Effort to upgrade**: half a day if you bring jline back, or a week if
you want to keep the JNA-free build (would mean writing a small VT100
key-reader by hand).

## Page rendering uses `body.view` only

We always render from Confluence's `body.view` HTML. We also fetch
`body.export_view` and `body.editor2` because some macros need the
alternative renderings (Jira tables, ToC, page-properties report all live
in `export_view`; PlantUML and the markdown macro live in `editor2`).

But the conversion pipeline is hardcoded to `body.view` for the main
content. If a page has a macro whose `body.view` rendering is "Click here
to refresh" but the actual content is in `body.export_view`, we miss it.

**Effort to fix**: depends on the macro. Each one needs case-by-case
investigation. Add a switch in `ConfluencePageConverter.convertDiv` that
looks up the relevant `export_view` or `editor2` snippet and recursively
processes it.

## Attachment selection is body-text matching

`PageExporter.selectAttachmentsForExport` decides which attachments to
download by checking whether the attachment's `fileId` appears anywhere
in the page body string:

```java
if (a.fileId() != null && body.contains(a.fileId())) { out.add(a); }
```

This is the simplest possible match. It works because Confluence's HTML
includes the file ID in `<img data-media-id="...">` and
`<a data-linked-resource-file-id="...">`. But it's a substring match
without word boundaries, so a 6-char file ID could in theory false-match
on unrelated text. In practice file IDs are 36-char GUIDs so this is safe,
but it's not bulletproof.

**Effort to harden**: an hour. Replace `body.contains` with a regex on
`data-(?:media-id|linked-resource-file-id)="<id>"`.

To bypass selection entirely: `jcme config set export.attachment_export_all=true`
downloads every attachment, referenced or not.

## Drawio mermaid extraction needs the file already on disk

[ConfluencePageConverter.readDrawioMermaid](../src/main/java/de/skerkewitz/jcme/markdown/ConfluencePageConverter.java)
reads the `.drawio` source file from disk to find an embedded Mermaid
diagram. The file has to have been **downloaded** first.

The export pipeline does download attachments before rendering markdown,
so this works for normal exports. But if someone disables attachment
export (`attachment_export_all=false` and the `.drawio` isn't referenced
by file ID — only by `diagramName=` in the body), the download is skipped
and the mermaid extraction silently falls back to embedding the PNG
instead.

**Effort to fix**: medium. Make the markdown converter trigger a
just-in-time download for drawio attachments it encounters.

## Unicode-whitespace normalization not ported

The Python original has a workaround in `convert_em` / `convert_strong` /
`convert_code` etc. that normalizes Unicode whitespace (e.g. NBSP, U+200B
ZWSP) to plain spaces, because markdownify's `chomp()` strips them
entirely and produces wrong output.

We use jsoup, which has different (better) chomping behaviour, so this
might not be needed — but I haven't tested with pages full of Confluence's
notorious "non-breaking space" overuse. If output looks wrong around
emphasis boundaries (`word*emphasis*` instead of `word *emphasis*`),
this is the place to look.

**Effort to add**: an hour, with a few golden tests.

## Limited macro coverage

We handle the common Confluence macros (alerts, expand, columnLayout,
attachments, drawio, plantuml, jira issue/table, ToC, markdown,
scroll-ignore, details/page-properties, qc-read-and-understood). But
Confluence has dozens of macros that customers and Marketplace apps add.
Anything we don't recognize falls through to pass-through `<div>`
rendering — children render, macro wrapper is dropped — which usually
produces *something* sensible but not always the intended output.

Common missing handlers:
- `gallery` (Confluence's image gallery)
- `recently-updated`, `recently-used-labels`
- `livesearch`, `pagetree`
- `iframe` macro (different from raw `<iframe>`)
- `viewfile` (renders an embedded preview of an office file)
- Many Marketplace-app-specific macros

**Effort per macro**: depends. Most are 30–60 minutes to add a converter
case + a fixture-based test.

## No streaming attachment download

`ConfluenceClient.downloadAttachment` returns `byte[]`. For a 500 MB
PowerPoint pinned to a wiki page, that's 500 MB held in JVM heap during
download. Not great.

**Effort to fix**: medium. Switch to
`HttpResponse.BodyHandlers.ofFile(target)` and stream straight to disk.
Requires reworking the retry loop because partial files would need
cleanup on failure.

## No resume-on-failure mid-page

If the JVM crashes mid-export, the lockfile reflects the last successful
`recordPage` call. Re-running picks up where you left off (skip-unchanged
will skip already-recorded pages). But within a single page, a partial
markdown write is invisible to the lockfile — the next run sees that
page as "not yet exported" and starts over.

This is fine for typical exports but wasteful for the case of a single
huge page with many large attachments. Not worth fixing unless someone
reports a real-world pain point.

## Auth Cloud OAuth flow not supported

We support API tokens (Cloud) and PATs (Server/DC). We do **not**
support the OAuth 2.0 flow that some enterprise Cloud setups require for
scoped tokens. The cloud-id auto-discovery routes through the API gateway
which works with API tokens but not all OAuth scopes.

**Effort**: a real project. OAuth 2.0 means a local callback server,
browser flow, refresh token handling, and storing tokens securely. 1–2
weeks.

## No proxy support

The HTTP client uses the JVM's default proxy selector (which honors
`http.proxyHost`/`https.proxyHost` system properties). There's no
config-file way to set a per-instance proxy. Workaround: use system
properties:

```sh
java -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080 -jar jcme.jar pages ...
```

**Effort**: an hour to add per-instance proxy fields, plus tests.

## Filename sanitization is configurable but not great by default

The default character map (`export.filename_encoding`) just replaces
`<>:"/\|?*[]'´’\``` with `_`. So a page titled `What's *new* in 2024?`
becomes `What_s _new_ in 2024_.md`. Functional but not pretty.

**Effort to improve**: ~2 hours. Tune the default replacement map (or
introduce smart Unicode-aware replacements). The mechanism is fully in
[FilenameSanitizer.java](../src/main/java/de/skerkewitz/jcme/export/FilenameSanitizer.java)
and the encoding setting accepts JSON-style char-to-replacement pairs.

## Lockfile grows unboundedly

Pages stay in the lockfile until they're confirmed deleted from
Confluence (via `StaleCleanup`). If you've exported and then deleted a
URL from your config, the lockfile entries from that base URL never get
cleaned up because `seenBaseUrls` doesn't include it anymore.

This is mostly harmless (a few KB per 100 pages) but accumulates. Could
be addressed by an explicit `jcme prune` command.

## What I'd build next

Priority list if I were taking another week with this code:

1. **Validate against a real Confluence**. Without this, half the
   limitations above are theoretical. (4 hours)
2. **Live progress bar with proper ANSI redraw + non-TTY plain
   fallback**. The biggest UX win for typical multi-thousand-page
   exports. (1 day)
3. **Run native-image** end to end and commit the captured metadata.
   Native binary is the most-requested distribution format. (1 day,
   including iteration)
4. **Streaming attachment downloads** to avoid large-file OOM. (2 hours)
5. **Per-base-URL proxy + cert override** for corporate environments.
   (4 hours)
6. **A `jcme prune` command** to clean up lockfile entries from URLs no
   longer in the config. (2 hours)

Beyond that, the macro-coverage list grows organically — add converters
as users hit the gaps.
