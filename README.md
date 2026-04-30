# jconfluence-markdown-exporter

A Java port of [confluence-markdown-exporter](https://github.com/Spenhouet/confluence-markdown-exporter)
by Sebastian Penhouet. Exports Confluence pages, page trees, spaces, or whole organizations
to Markdown files for Obsidian, GitHub, Azure DevOps, Foam, Dendron, and similar tools.

This port targets feature parity with the Python original; see [Status](#status) for what's
already in place and what's deferred.

## Highlights

- Confluence Cloud (`*.atlassian.net`), Atlassian API gateway, and self-hosted Server / Data
  Center URL formats — including context-pathed installs.
- HTML → Markdown conversion with Confluence-specific extensions: GitHub-style alerts from
  panel/info/note/tip/warning macros, page-properties macro → YAML front matter, drawio
  diagrams, PlantUML, page links, attachment links, user mentions, emoticons, task lists,
  expand container, column layout, jira issue + table macros, ToC, markdown macro.
- Skip-unchanged via a JSON lockfile keyed by page id + version + export path.
- Stale-file cleanup: pages deleted in Confluence between runs are removed locally.
- Parallel page export with a configurable worker pool.
- Interactive credential setup that pre-fills the URL when an export hits an
  `AuthNotConfigured` error.
- Cross-platform: Windows, Linux, macOS.

## Build

Requires JDK 21 or newer. Both Gradle and Maven build descriptors are provided and produce
equivalent runnable artifacts.

### Gradle

```bash
./gradlew build         # compile + run all tests
./gradlew installDist   # produces build/install/jcme/{bin,lib}
./gradlew distZip       # build/distributions/jcme-<version>.zip
./gradlew distTar       # build/distributions/jcme-<version>.tar
```

The `jcme` start scripts land in `build/install/jcme/bin/` (Unix shell + Windows `.bat`).
Add that directory to your `PATH`, or invoke the script directly.

### Maven

```bash
mvn package             # compile + test + produce two artifacts (see below)
mvn test                # tests only
```

`mvn package` produces two equivalent artifacts:

1. **Launcher scripts** at `target/appassembler/{bin,lib}/` — produced by the
   `appassembler-maven-plugin`. This is the Maven equivalent of Gradle's `installDist`
   and gives you a directly-invokable `jcme` command:

   ```bash
   target/appassembler/bin/jcme --help            # Unix shell
   target\appassembler\bin\jcme.bat --help        # Windows
   ```

   Add `target/appassembler/bin/` to your `PATH` to call `jcme` from anywhere. The
   start scripts already pass `--enable-native-access=ALL-UNNAMED` to silence the JDK
   24+ JNA warning on first call.

2. **Uber-jar** at `target/jcme-<version>.jar` — produced by `maven-shade-plugin`.
   Useful for one-off invocations or for handing a single file to a colleague:

   ```bash
   java --enable-native-access=ALL-UNNAMED -jar target/jcme-0.1.0-SNAPSHOT.jar --help
   ```

#### Repo-root wrappers (`jcmew.sh` / `jcmew.bat`)

If you don't want to put `target/appassembler/bin/` on `PATH`, the repo ships small
wrappers — same idea as `gradlew`/`mvnw`:

```bash
./jcmew.sh --help                       # Unix
jcmew.bat --help                        # Windows
```

Both find `target/jcme-*.jar`, pass `--enable-native-access=ALL-UNNAMED` to the JVM,
and forward any remaining arguments. They print a friendly error if you haven't run
`mvn package` yet.

> The two build files are kept in sync by hand. If you change a dependency version,
> update both [build.gradle.kts](build.gradle.kts) and [pom.xml](pom.xml).

#### GraalVM native-image (true native binary)

A real ahead-of-time-compiled native executable (no JVM at runtime) isn't built out of
the box: it requires reflection / resource hints for Jackson, jsoup, jline, picocli, and
the SLF4J service-loader files, plus a working GraalVM toolchain. This is on the roadmap
but not wired up yet.

## Logging

All log output is written to **stderr**. The default level is **INFO**, which prints
high-level progress (URL resolution, page fetches, attachment downloads, file writes).

Bump the level with either of:

```sh
jcme config set export.log_level=DEBUG          # persists to config file
JCME_EXPORT__LOG_LEVEL=DEBUG jcme pages …       # one-off run
```

`DEBUG` adds:

- Per-HTTP-request URLs with elapsed time and response size
- Cache hit/miss for spaces and pages
- Per-batch paging info for attachments and descendants
- Per-worker thread tags for parallel exports

If a run appears to hang, the most likely culprits visible in DEBUG are:

- An HTTP request that hasn't returned within the configured `connection_config.timeout`
  (default 30 s) — you'll see `HTTP GET https://… (attempt N/M, timeout 30s)` with no
  follow-up "returned in X ms" line.
- A retry loop on a flaky endpoint — look for `HTTP 503 from … — retrying in Ns`.
- The cloud-id auto-probe at `https://*.atlassian.net/_edge/tenant_info` (5 s cap).

To redirect logs to a file, just use shell redirection:

```sh
jcme pages …  2> jcme.log
```

## First-run setup

The CLI is `jcme`. With no arguments it prints help.

```sh
jcme config              # opens the interactive configuration menu
```

Or, more concretely: just attempt an export. The tool will detect that auth isn't set up
and drop you into a credential prompt with the URL pre-filled:

```sh
jcme pages https://your-company.atlassian.net/wiki/spaces/KEY/pages/123/Some-Page
```

You'll be prompted for username (your email for Cloud), API token, optional Personal Access
Token (for self-hosted Server/DC), Cloud ID (auto-detected for `*.atlassian.net`), and a
REST API URL override (only relevant for split-host deployments — see below).

For Atlassian Cloud, generate an API token at
<https://id.atlassian.com/manage-profile/security/api-tokens>.

### Split-host deployments (REST API on a different host than the page URL)

Some corporate Confluence installations serve the HTML frontend and the REST API from
different hostnames behind a reverse proxy. Example:

| What            | URL                                                                |
| --------------- | ------------------------------------------------------------------ |
| HTML page URL   | `https://confluence.axa.com/confluence/spaces/DEITGW/pages/345/X`  |
| REST API URL    | `https://confluencews.axa.com`                                     |

In that case, configure auth keyed by the **page URL host** (so URL parsing keeps working
for the URLs you paste from the browser), and set `api_url` to the **REST API host**.
The interactive menu prompts for it after Cloud ID:

```sh
jcme config edit auth.confluence
# → "Edit credentials" on the existing entry, or "Add new instance"
# → REST API URL (only set if the API is on a different host than the page URL):
#   https://confluencews.axa.com
```

When `api_url` is set, jcme uses that hostname for every REST call (page fetches,
attachment downloads, CQL searches) while still recognizing the page-URL host on the
command line. The cloud-id auto-probe is skipped — it's a deliberate signal that you're
not on Atlassian Cloud's standard topology.

## Usage

```sh
# Single page
jcme pages https://company.atlassian.net/wiki/spaces/KEY/pages/123/Title

# Multiple pages at once
jcme pages https://...page1 https://...page2

# Whole page tree (page + descendants)
jcme pages-with-descendants https://...root-page

# Whole space (homepage + descendants)
jcme spaces https://company.atlassian.net/wiki/spaces/MYSPACE

# Every space in an organization
jcme orgs https://company.atlassian.net

# Singular aliases also work: page / page-with-descendants / space / org
```

Supported page URL formats:

| Form           | Example                                                                                                       |
| -------------- | ------------------------------------------------------------------------------------------------------------- |
| Cloud          | `https://company.atlassian.net/wiki/spaces/KEY/pages/123456789/Page+Title`                                    |
| Cloud gateway  | `https://api.atlassian.com/ex/confluence/CLOUDID/wiki/spaces/KEY/pages/123456789/Page+Title`                  |
| Server long    | `https://wiki.company.com/display/KEY/Page+Title`                                                             |
| Server short   | `https://wiki.company.com/KEY/Page+Title`                                                                     |
| Server `?pageId=` | `https://wiki.company.com/pages/viewpage.action?pageId=123456789`                                          |

## Configuration

The configuration file lives at the OS-standard application config directory:

| OS      | Path                                                                  |
| ------- | --------------------------------------------------------------------- |
| Linux   | `~/.config/jconfluence-markdown-exporter/app_data.json`               |
| macOS   | `~/Library/Application Support/jconfluence-markdown-exporter/app_data.json` |
| Windows | `%APPDATA%\jconfluence-markdown-exporter\app_data.json`               |

Override the location with `JCME_CONFIG_PATH=/path/to/file.json`.

`jcme config path` prints the resolved path.

### Subcommands

```sh
jcme config                       # interactive menu
jcme config list                  # print full config as YAML
jcme config list -o json          # ... or JSON
jcme config get export.log_level
jcme config set export.log_level=DEBUG
jcme config set export.skip_unchanged=false connection_config.max_workers=5
jcme config edit auth.confluence  # interactive credential editor
jcme config reset                 # reset everything (with confirmation)
jcme config reset export.log_level
jcme config path
jcme bugreport                    # version + redacted config for issue reports
```

`set` accepts JSON values when possible (`true`/`false`/numbers/arrays), otherwise plain
strings.

### Available config keys

| Key                                       | Default                                                                  | Description                                                                                       |
| ----------------------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------- |
| `export.output_path`                      | `""` (current directory)                                                 | Directory where exported files are saved.                                                         |
| `export.log_level`                        | `INFO`                                                                   | `DEBUG` / `INFO` / `WARNING` / `ERROR`. `DEBUG` also forces serial export.                        |
| `export.skip_unchanged`                   | `true`                                                                   | Skip pages whose version + export path match the lockfile.                                        |
| `export.cleanup_stale`                    | `true`                                                                   | Delete local files whose page was removed from Confluence.                                        |
| `export.page_path`                        | `{space_name}/{homepage_title}/{ancestor_titles}/{page_title}.md`        | File path template for pages.                                                                     |
| `export.attachment_path`                  | `{space_name}/attachments/{attachment_file_id}{attachment_extension}`    | File path template for attachments.                                                               |
| `export.page_href` / `attachment_href`    | `relative`                                                               | Link style: `relative`, `absolute`, or `wiki` (Obsidian-style `[[...]]`).                         |
| `export.include_document_title`           | `true`                                                                   | Prepend an H1 title to each page.                                                                 |
| `export.page_breadcrumbs`                 | `true`                                                                   | Include breadcrumb links at the top of each page.                                                 |
| `export.page_properties_as_front_matter`  | `true`                                                                   | Convert the page-properties macro into YAML front matter.                                         |
| `export.attachment_export_all`            | `false`                                                                  | Export all attachments instead of only the ones referenced by the page.                           |
| `export.enable_jira_enrichment`           | `true`                                                                   | Look up Jira issues to enrich Confluence Jira issue links with the issue summary.                 |
| `export.filename_encoding`                | (Windows-safe map)                                                       | JSON-style char-to-replacement map applied during filename sanitization.                          |
| `export.filename_length`                  | `255`                                                                    | Maximum filename length.                                                                          |
| `export.filename_lowercase`               | `false`                                                                  | Force all filenames to lowercase.                                                                 |
| `export.lockfile_name`                    | `confluence-lock.json`                                                   | Name of the lockfile written under `output_path`.                                                 |
| `export.existence_check_batch_size`       | `250`                                                                    | Page IDs per existence-check batch (capped to 25 internally for the v1 CQL endpoint).             |
| `connection_config.max_workers`           | `20`                                                                     | Parallel page-export workers. Set to `1` to force serial mode.                                    |
| `connection_config.use_v2_api`            | `false`                                                                  | Use Confluence v2 REST endpoints where available (Cloud + DC 8+).                                 |
| `connection_config.verify_ssl`            | `true`                                                                   | Verify HTTPS certificates.                                                                        |
| `connection_config.timeout`               | `30`                                                                     | API request timeout in seconds.                                                                   |
| `connection_config.backoff_and_retry`     | `true`                                                                   | Enable exponential-backoff retry on configured status codes.                                      |
| `connection_config.backoff_factor`        | `2`                                                                      | Multiplier for backoff between retries.                                                           |
| `connection_config.max_backoff_seconds`   | `60`                                                                     | Cap on per-attempt backoff sleep.                                                                 |
| `connection_config.max_backoff_retries`   | `5`                                                                      | Maximum retry attempts.                                                                           |
| `connection_config.retry_status_codes`    | `[413, 429, 502, 503, 504]`                                              | HTTP status codes that trigger a retry.                                                           |
| `auth.confluence`                         | `{}`                                                                     | URL-keyed map of credentials. Use `jcme config edit auth.confluence`.                             |
| `auth.jira`                               | `{}`                                                                     | URL-keyed map of Jira credentials.                                                                |

### Path-template variables

`export.page_path` and `export.attachment_path` use `{var}`-style substitution. Available
variables:

- `{space_key}`, `{space_name}`
- `{homepage_id}`, `{homepage_title}`
- `{ancestor_ids}`, `{ancestor_titles}` (slash-separated)
- Pages: `{page_id}`, `{page_title}`
- Attachments: `{attachment_id}`, `{attachment_title}` (without extension),
  `{attachment_file_id}` (a GUID), `{attachment_extension}` (with leading dot)

Unknown placeholders are left literal.

### Environment variable overrides

Any setting can be overridden with `JCME_<SECTION>__<FIELD>` (note the **double**
underscore). Values are JSON-parsed first, then fall back to plain strings.

```sh
JCME_EXPORT__LOG_LEVEL=DEBUG
JCME_EXPORT__OUTPUT_PATH=/tmp/export
JCME_CONNECTION_CONFIG__MAX_WORKERS=5
JCME_CONNECTION_CONFIG__VERIFY_SSL=false
```

ENV vars override file values for the duration of the run; they are not persisted.

## Status

Phases of the port (see commit history for details):

- ✅ **Phase 0–1**: project skeleton, configuration storage, env-var overlay,
  `config get/set/list/path/reset`, `bugreport`, `version`.
- ✅ **Phase 2**: REST client (HTTP/2 via `java.net.http.HttpClient`), URL parsing for
  Cloud/Server/gateway/`?pageId=` formats, retry/backoff, SSL trust-all toggle,
  per-base-URL client cache, Cloud-ID auto-fetch.
- ✅ **Phase 3**: typed records mirroring the Python pydantic models, `ConfluenceFetcher`
  with cached lookups for pages/spaces/users/Jira issues, MIME → extension table,
  filename sanitizer, path templates.
- ✅ **Phase 4–5**: HTML → Markdown converter built on jsoup, Confluence-specific
  converters for all the macros enumerated above, page renderer that assembles
  front-matter + breadcrumbs + body + placeholder escaping.
- ✅ **Phase 6**: end-to-end export pipeline — atomic file writes, per-page worker, lockfile
  manager, attachment downloader, stale-page cleanup, parallel runner. CLI commands
  `pages` / `pages-with-descendants` / `spaces` / `orgs` are all wired.
- ✅ **Phase 8**: interactive configuration menu, including the auth-failure recovery flow
  that pre-fills the URL and walks the user through credential entry.

Deferred:

- 🚧 **Rich progress UI** (Phase 7). The current summary is plain text; the Python
  original uses `rich` for a fancier panel.
- 🚧 **GraalVM native-image build**. Would give native startup speed; needs reflection
  hints for Jackson + jsoup. Out of scope for first cut.

The Python project's full test fixture set has not been ported; instead the Java tests use
in-process `HttpServer`-backed integration tests for the export pipeline and unit tests
for the conversion logic.

## Differences from the Python original

- Java 21 records replace pydantic models.
- The configuration overlays use `JCME_` instead of `CME_` as the env-var prefix and
  `jconfluence-markdown-exporter` as the OS app-config directory name. Configuration
  files are not shared between the two tools.
- Concurrency uses `Executors.newFixedThreadPool(maxWorkers)` rather than a
  `ThreadPoolExecutor` with thread-local clients (Java's `HttpClient` is thread-safe so
  the per-thread cache is unnecessary).
- The interactive config menu uses simple numbered selections rather than `questionary`'s
  arrow-key UI; functionally equivalent for the auth setup flow.
- `rich`-styled console output is replaced with plain text. CI mode (`NO_COLOR` /
  `CI=true`) doesn't yet have a special branch; output is plain text either way.

## Project layout

```
src/main/java/de/skerkewitz/jcme/
├── App.java                  # picocli root command
├── AppInfo.java              # name + version constants
├── api/                      # REST client + URL parsing + auth lookup
├── cli/                      # picocli subcommands
│   └── config/               # config + interactive menu
├── config/                   # AppConfig records + ConfigStore
├── export/                   # Page / attachment / parallel runner / stale cleanup
├── fetch/                    # ConfluenceFetcher (typed REST → records)
├── lockfile/                 # ConfluenceLock + LockfileManager
├── markdown/                 # HTML → Markdown converter + page renderer
├── model/                    # Page, Space, Attachment, Version, ...
└── utils/                    # Drawio mermaid extraction
```

## License

[MIT](LICENSE). The Python original is also MIT-licensed; the original
copyright remains with Sebastian Penhouet.
