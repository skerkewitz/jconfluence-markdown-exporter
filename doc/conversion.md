# HTML → Markdown conversion

How `jcme` turns Confluence's rendered `body.view` HTML into Markdown.
Every tag's behaviour is defined either in
[DefaultConverters.java](../src/main/java/de/skerkewitz/jcme/markdown/DefaultConverters.java)
(plain HTML rules) or
[ConfluencePageConverter.java](../src/main/java/de/skerkewitz/jcme/markdown/ConfluencePageConverter.java)
(Confluence-specific overrides). Tables go through
[TableConverter.java](../src/main/java/de/skerkewitz/jcme/markdown/TableConverter.java).

## How the renderer is wired together

```
PageRenderer.render(page)
    │
    ├─ Build HTML to convert:
    │     "<h1>" + page.title() + "</h1>" + page.body()   if include_document_title=true
    │     page.body()                                       otherwise
    │
    ├─ ConfluencePageConverter (extends MarkdownConverter)
    │     └─ jsoup parses HTML → DOM
    │     └─ Walk the DOM depth-first; for each element call
    │        the registered NodeConverter (or PASSTHROUGH)
    │     └─ tidy() collapses runs of 3+ newlines to 2
    │
    ├─ PlaceholderEscaper.escape(body)
    │     └─ <unknown-thing> → \<unknown-thing\>  (preserves valid HTML tags + code)
    │
    ├─ FrontMatterRenderer.render(pageProperties, tags)
    │     └─ "---\n<YAML>\n---\n"
    │
    └─ Concat: frontMatter + breadcrumbs + body
```

`pageProperties` is populated as a side effect of the `details` macro
converter — it accumulates rows from the page-properties macro into the
front matter map.

## Plain-HTML rules (DefaultConverters)

These apply to vanilla HTML — no Confluence specifics. Confluence sometimes
emits these tags directly, sometimes wrapped in macros.

### Block elements

| HTML | Markdown | Notes |
| --- | --- | --- |
| `<h1>` … `<h6>` | `# X` … `###### X` | ATX style. Internal whitespace is collapsed, surrounded by blank lines. |
| `<p>text</p>` | `text` (with surrounding blank lines) | Inside table cells: collapses to text + `<br/>`. |
| `<br>` / `<br/>` | `  ` + newline | Two trailing spaces is the markdown line-break syntax. |
| `<hr>` | `\n\n---\n\n` | Thematic break. |
| `<blockquote>X</blockquote>` | Each line prefixed with `> ` | Multi-line preserved. |

### Inline emphasis

| HTML | Markdown |
| --- | --- |
| `<strong>X</strong>` | `**X**` |
| `<b>X</b>` | `**X**` |
| `<em>X</em>` | `_X_` |
| `<i>X</i>` | `_X_` |
| `<s>X</s>` | `~~X~~` |
| `<strike>X</strike>` | `~~X~~` |
| `<del>X</del>` | `~~X~~` |
| `<u>X</u>` | `<u>X</u>` (kept raw — no markdown equivalent) |
| `<sub>X</sub>` | `<sub>X</sub>` (raw HTML) |
| `<sup>X</sup>` | `<sup>X</sup>` in plain mode; **footnote syntax** in Confluence mode (see below) |

The `chomp` step preserves leading/trailing whitespace inside emphasis tags.
Empty content (e.g. `<strong></strong>`) produces nothing.

### Code

| HTML | Markdown |
| --- | --- |
| `<code>x</code>` | `` `x` `` |
| `<pre><code>foo</code></pre>` | Fenced code block ` ``` ` |
| `<pre><code class="language-java">int x;</code></pre>` | ` ```java ` … ` ``` ` |
| `<kbd>X</kbd>` | `<kbd>X</kbd>` (raw) |
| `<samp>X</samp>` | `<samp>X</samp>` (raw) |
| `<var>X</var>` | `<var>X</var>` (raw) |

`<code>` inside `<pre>` is unwrapped (no double backticks). Standalone
`<code>` becomes inline. Empty `<code></code>` is dropped entirely.

### Lists

| HTML | Markdown |
| --- | --- |
| `<ul><li>A</li><li>B</li></ul>` | `- A\n- B` |
| `<ol><li>A</li><li>B</li></ol>` | `1. A\n2. B` |
| `<ol start="5"><li>A</li>` | `5. A` |
| Nested lists | Indented by 2 spaces per level |

The bullet character is configurable via the (currently default-only)
`ConversionOptions.bullet` field — `-` by default.

### Links and images

| HTML | Markdown |
| --- | --- |
| `<a href="x">text</a>` | `[text](x)` |
| `<a href="x" title="t">text</a>` | `[text](x "t")` |
| `<a href="">text</a>` | `text` (no link) |
| `<a href="x"></a>` | `[x](x)` (uses href as link text) |
| `<img src="a.png" alt="alt">` | `![alt](a.png)` |
| `<img src="a.png" alt="alt" title="t">` | `![alt](a.png "t")` |
| `<img src="" alt="alt">` | `alt` (no image) |

Note: these rules apply to **plain** anchors and images. Confluence-flavored
anchors (page links, attachment links) are handled separately — see below.

### Containers

| HTML | Markdown |
| --- | --- |
| `<div>`, `<span>`, `<section>`, `<article>`, `<header>`, `<footer>`, `<main>`, `<nav>`, `<aside>`, `<figure>`, `<figcaption>` | Pass-through (children rendered, tag dropped) |
| `<time>`, `<abbr>`, `<cite>`, `<small>` | Pass-through (in plain mode; Confluence mode overrides `<time>`) |
| `<mark>X</mark>` | `==X==` (Obsidian / Pandoc highlight) |

### Tables

The default converter delegates to
[TableConverter.convertTable](../src/main/java/de/skerkewitz/jcme/markdown/TableConverter.java).
Output is GitHub-flavored Markdown pipe-tables:

| HTML | Markdown |
| --- | --- |
| `<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>` | `\| A \| B \|` + separator + data rows |
| Cell containing `\|` | Escaped to `\|` |
| Cell containing `\n` | Replaced with `<br/>` |
| Cell with `<p>` | Inline (paragraph collapses to `<br/>` boundaries) |
| `<th>` row at top | Recognized as header; gets `\| --- \|` separator row |
| No `<th>`s | Empty header row inserted to satisfy MD spec |
| `<td colspan="N">` | Cell padded with N-1 empty cells |
| `<td rowspan="N">` | N empty cells emitted in subsequent rows |
| `<thead>`, `<tbody>`, `<tfoot>`, `<tr>` | Pass-through (the `<tr>`s are already collected by `convertTable`) |

The `pad()` algorithm walks rows left-to-right tracking which `(row, col)`
slots are already occupied by a rowspan from a higher row. This produces
correct alignment even for messy `colspan`+`rowspan` combinations.

### Skipped tags

| HTML | Output |
| --- | --- |
| `<script>...</script>` | empty (children dropped entirely) |
| `<style>...</style>` | empty |
| `<head>...</head>` | empty |
| `<noscript>...</noscript>` | empty |

These live in [`MarkdownConverter.SKIP_TAGS`](../src/main/java/de/skerkewitz/jcme/markdown/MarkdownConverter.java).

### Anything else

Any HTML tag with no registered converter falls through to `PASSTHROUGH`,
which emits the children's text but drops the tag itself. So an unknown
`<custom-element>foo</custom-element>` becomes `foo`.

## Confluence-specific rules (ConfluencePageConverter)

These overrides are registered after the defaults and take precedence. They
detect Confluence-isms via attributes (`data-macro-name`, `data-jira-key`,
`data-linked-resource-type`, etc.) and emit specialised output.

### Macros (via `<div data-macro-name="...">`)

Dispatched in `ConfluencePageConverter.convertDiv`:

| Macro name | Markdown output |
| --- | --- |
| `info` | GitHub `> [!IMPORTANT]` blockquote |
| `panel` | GitHub `> [!NOTE]` blockquote |
| `tip` | GitHub `> [!TIP]` blockquote |
| `note` | GitHub `> [!WARNING]` blockquote |
| `warning` | GitHub `> [!CAUTION]` blockquote |
| `details` | **Removed inline**; rows become page-properties YAML front matter |
| `drawio` | Markdown image-wrapped link to the `.drawio.png` preview + `.drawio` source attachment, **or** Mermaid code block when extractable from the drawio XML |
| `plantuml` | ` ```plantuml` code block. Source pulled from `editor2` XML (`<plain-text-body>` JSON's `umlDefinition`) |
| `scroll-ignore` | `<!-- ... -->` HTML comment (content preserved) |
| `toc` | Whatever Confluence rendered into `body.export_view`'s `div.toc-macro` |
| `jira` (in `<div>`) | Whatever Confluence rendered into `body.export_view`'s `div.jira-table` |
| `attachments` | Markdown table listing every attachment on the page (file link + last-modified by user) |
| `markdown` / `mohamicorp-markdown` | Raw markdown content extracted from the macro body |
| `qc-read-and-understood-signature-box` | empty (explicitly ignored) |

When a macro name isn't recognized, the children pass through with no special
treatment.

### `<div class="...">` (no macro name)

| Class contains | Output |
| --- | --- |
| `expand-container` | `<details>\n<summary>X</summary>\n\nbody\n\n</details>` |
| `columnLayout` (with 2+ `.cell` children) | A 1-row table, one column per cell |
| Anything else | Pass-through |

### `<span data-macro-name="jira">` — Jira issue link

If `enable_jira_enrichment=true` and we can resolve the issue:

```
[[KEY] Issue summary](https://jira.example.com/browse/KEY)
```

If enrichment is disabled or the issue lookup fails, we fall back to:

```
[[KEY](https://jira.example.com/browse/KEY)
```

(yes, the `[[` is literal — matches the Python original's behavior).

The Jira URL is extracted from the link's `href` attribute via
`UrlParsing.extractJiraBaseUrl`, then routed through the Jira API client
configured for that base URL.

### `<a>` — anchors with Confluence context

Resolved in `ConfluencePageConverter.convertA`, in this order:

1. **`class="user-mention"` with `data-account-id`** — call
   `fetcher.getUserByAccountId`, render display name with `(Unlicensed)`/
   `(Deactivated)` suffixes stripped.
2. **`href` contains `createpage.action`** or **`class` contains `createlink`** —
   broken link (Confluence emits these for not-yet-created pages). Output:
   `[[text]]`.
3. **`data-linked-resource-type="page"`** — page link. Resolves the target
   page via `fetcher.getPage(id)`, computes the relative export path, emits
   `[Page Title](relative/path.md)`.
4. **`data-linked-resource-type="attachment"`** — attachment link. Looks up
   the attachment by file ID (or attachment ID) on the current page, emits
   `[Title](attachments/file-guid.ext)`.
5. **Path matches a Confluence URL pattern** — same as case 3 (resolves the
   page).
6. **`href` starts with `#`** — anchor link. Output:
   `[text](#github-flavored-slug)` via [HeadingSlugger](../src/main/java/de/skerkewitz/jcme/markdown/HeadingSlugger.java).
7. **Otherwise** — fallback to default link rendering.

When `attachment_href=wiki` is set, attachment links use Obsidian-style
`[[file.png|Title]]` instead of `[Title](file.png)`. Same for `page_href=wiki`
with page links: `[[Page Title]]`.

### `<img>` — images and emoticons

In order:

1. **`class` contains `emoticon`** — return the emoji:
   - From `data-emoji-id` if it's a hex codepoint (e.g. `1f60a` → 😊).
   - From `data-emoji-id` if it matches the Atlassian-emoticon map
     (e.g. `atlassian-check_mark` → ✅). See
     [EmoticonMap.java](../src/main/java/de/skerkewitz/jcme/markdown/EmoticonMap.java).
   - From `data-emoji-shortname` or `data-emoji-fallback` or `alt` as a last resort.
2. **`src` contains `.drawio.png`** — try to extract a Mermaid diagram from
   the corresponding `.drawio` attachment. Falls back to embedding the PNG
   if no Mermaid is found.
3. **Find an attachment** by `data-media-id` / `data-linked-resource-id` and
   emit `![alt](attachments/file-guid.ext)`.
4. **External image** (`<img src="https://...">`) — keep the URL as-is:
   `![alt](https://...)`.

### `<li>` — task lists

Detected by `data-inline-task-id` attribute:

| Attributes | Markdown |
| --- | --- |
| `data-inline-task-id` (no class) | `- [ ] Task text` |
| `data-inline-task-id class="checked"` | `- [x] Task text` |
| Plain `<li>` | Default: `- text` (or `1. text` inside `<ol>`) |

### `<pre>` — code blocks with brush hint

Confluence's syntax highlighter emits `<pre data-syntaxhighlighter-params="brush: java; ...">`.
The Confluence converter parses out the `brush` param to set the language tag:

```
<pre data-syntaxhighlighter-params="brush: java;"><code>int x;</code></pre>
```

becomes

````
```java
int x;
```
````

Falls back to the default `<pre>` handling (looking for `<code class="language-...">`)
if no `brush` param is found.

### `<sup>` — footnotes

In Confluence mode (overrides the plain default):

| Position | Markdown |
| --- | --- |
| First child of its parent (`previousSibling == null`) | `[^X]:` (footnote definition) |
| Otherwise | `[^X]` (footnote reference) |

This matches Confluence's convention of writing definitions like `<p><sup>1</sup> Footnote text</p>` and references inline like `<p>see this<sup>2</sup></p>`.

### `<time>` — datetime

If the element has a `datetime` attribute, use that value (typically ISO 8601);
otherwise fall back to the visible text.

```html
<time datetime="2024-01-15">January 15</time>  →  2024-01-15
```

### `<table class="metadata-summary-macro">` — page properties report

Special-cased in `ConfluencePageConverter.convertTable`. The `data-cql`
attribute identifies which page-properties report this is. The actual
rendered table comes from `body.export_view`, not `body.view`. The table
content is then run through the standard `TableConverter`.

## Front matter

Generated by [FrontMatterRenderer.render(pageProperties, tags)](../src/main/java/de/skerkewitz/jcme/markdown/FrontMatterRenderer.java).

`pageProperties` is populated by the `details` macro converter (key-value
rows in the page-properties macro become `key: value` pairs). `tags` is the
list of page labels prefixed with `#`.

```yaml
---
status: Active
owner: Alice
tags:
  - "#release-notes"
  - "#draft"
---
```

YAML emitted via Jackson with `MINIMIZE_QUOTES` enabled.

If neither pageProperties nor tags has anything, the front matter block is
omitted entirely.

## Breadcrumbs

When `export.page_breadcrumbs=true`, the page renderer prepends a chain of
markdown links to each ancestor:

```
[Top](../top.md) > [Mid](../mid.md)
```

Built in `PageRenderer.renderBreadcrumbs`. Inaccessible ancestors render as
`[Page not accessible (ID: N)]`.

## Placeholder escaping

After conversion, [PlaceholderEscaper.escape(body)](../src/main/java/de/skerkewitz/jcme/markdown/PlaceholderEscaper.java)
runs over the markdown to escape `<placeholder>` patterns that Obsidian's
renderer would mistake for HTML tags:

```
fill in <your-name>      →   fill in \<your-name\>
```

What it preserves:
- Valid HTML tags (`<br/>`, `<em>`, `<details>`, all the actual HTML element names)
- HTML comments (`<!-- ... -->`)
- Code spans and fenced code blocks (entire content kept literal)

The list of "valid HTML tags" lives in `PlaceholderEscaper.HTML_ELEMENTS`.

## What's NOT converted

| Confluence feature | Why |
| --- | --- |
| External images (`<img src="https://other-domain/...">`) | URL kept as-is; no download |
| iframes / embedded content (Vimeo, YouTube, etc.) | Pass-through `<div>`/`<iframe>` → no special handling |
| Confluence "View File" macro for office docs | Currently rendered as a generic attachment link if linked from body, otherwise dropped |
| Inline comments / change tracking | Not in `body.view`, so invisible to us |
| Custom user macros that aren't in the recognized list | Pass-through (children render, macro wrapper dropped) |
| Whiteboards (Cloud feature) | Not exported — they aren't pages |

See [limitations.md](limitations.md) for what could be added.

## Final tidy

Once everything's concatenated, `MarkdownConverter.tidy(markdown)` collapses
runs of 3+ consecutive newlines into 2 (the standard MD paragraph separator),
then trims leading/trailing whitespace. This avoids the wide gap that would
otherwise appear when block converters that emit `\n\n…\n\n` get stacked.
