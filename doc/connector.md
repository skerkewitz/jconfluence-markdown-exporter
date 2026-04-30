# Connector

How `jcme` talks to Confluence: URL parsing, auth resolution, HTTP, retries,
and the per-base-URL client cache.

## At a glance

```
URL pasted by user
    ▼
UrlParsing.extractBaseUrl(url)            ── "the page-host URL"
    ▼
ApiClientFactory.getConfluence(baseUrl)
    ├── AuthLookup.find(...)              ── exact host → context-path-prefix
    ├── (optional) tryFetchCloudId(...)   ── for *.atlassian.net only
    ├── pick sdkUrl:
    │     api_url override?  → use it
    │     cloud_id present?  → api.atlassian.com/ex/confluence/<id>
    │     otherwise          → page-host URL
    ▼
ConfluenceClient(sdkUrl, HttpExecutor)
    ▼
HttpExecutor.send(...)  ── auth header, retry, body→bytes
    ▼
java.net.http.HttpClient.send(...)
```

The four players are all in `src/main/java/de/skerkewitz/jcme/api/`:

- [UrlParsing.java](../src/main/java/de/skerkewitz/jcme/api/UrlParsing.java) — URL normalisation + parsing
- [AuthLookup.java](../src/main/java/de/skerkewitz/jcme/api/AuthLookup.java) — host-based credential matching
- [HttpExecutor.java](../src/main/java/de/skerkewitz/jcme/api/HttpExecutor.java) — retry/backoff + auth headers + SSL
- [ApiClientFactory.java](../src/main/java/de/skerkewitz/jcme/api/ApiClientFactory.java) — per-base-URL client cache + cloud-id auto-discovery

Plus the typed wrappers [ConfluenceClient](../src/main/java/de/skerkewitz/jcme/api/ConfluenceClient.java)
and [JiraClient](../src/main/java/de/skerkewitz/jcme/api/JiraClient.java) on top.

## URL parsing

`UrlParsing.extractBaseUrl(url)` reduces any incoming URL to the **base** the
REST client should call. Cases handled:

| Input | Output | Notes |
| --- | --- | --- |
| `https://x.atlassian.net/wiki/spaces/K/pages/123/T` | `https://x.atlassian.net` | Cloud strips `/wiki/...` |
| `https://api.atlassian.com/ex/confluence/CLOUD/wiki/spaces/K` | `https://api.atlassian.com/ex/confluence/CLOUD` | Gateway preserved |
| `https://wiki.example.com/confluence/display/K/T` | `https://wiki.example.com/confluence` | Server context path preserved |
| `https://wiki.example.com:8443/display/K/T` | `https://wiki.example.com:8443` | Non-default port preserved |
| `https://wiki.example.com/pages/viewpage.action?pageId=42` | `https://wiki.example.com` | Query strings ignored at this level |

The "Confluence routing segments" (`wiki`, `display`, `spaces`, `rest`,
`pages`, `plugins`, `dosearchsite.action`) are listed in
[`UrlParsing.CONFLUENCE_ROUTE_SEGMENTS`](../src/main/java/de/skerkewitz/jcme/api/UrlParsing.java).
The first one encountered while walking the URL path stops the base-URL build
— anything before it is "context path". This is what makes
`https://wiki.company.com/confluence/spaces/K` correctly produce
`https://wiki.company.com/confluence` rather than `https://wiki.company.com`.

`parseConfluencePath(path)` then extracts `(spaceKey, pageId, pageTitle)` from
the path portion. Two regexes cover the cases:

- Cloud: `[/ex/confluence/<id>][/wiki]/spaces/<KEY>[/pages/<ID>[/<TITLE>]][/<extra>]`
- Server (long/short): `[/display]/<KEY>[/<TITLE>]`

`extractPageIdQueryParam(url)` handles the `?pageId=NNN` Server-style URL.
Case-insensitive on the key.

`+`-as-space decoding mirrors Python's `urllib.parse.unquote_plus`:
`Page+Title` decodes to `Page Title`.

## Auth lookup

Auth is stored as a JSON map keyed by **page-host URL**:

```json
{
  "auth": {
    "confluence": {
      "https://wiki.example.com/confluence": {
        "username": "alice@example.com",
        "api_token": "...",
        "pat": "",
        "cloud_id": "",
        "api_url": ""
      }
    }
  }
}
```

`AuthLookup.find(map, url)` resolves a request URL to the right entry:

1. **Exact match** on the normalised URL (trailing slashes stripped).
2. **Host + port match**, with two sub-rules:
   - If the stored key has **no context path**, it matches any path on the
     same host. So `https://wiki.example.com` covers both
     `https://wiki.example.com/display/X` and `https://wiki.example.com/foo`.
   - If the stored key **has** a context path, the request URL's path must
     start with it. So `https://wiki.example.com/confluence` matches
     `https://wiki.example.com/confluence/display/X` but not
     `https://wiki.example.com/jira/browse/Y`.
3. **API-gateway URLs** (`api.atlassian.com`) require an exact match — many
   Atlassian Cloud tenants share that hostname so host-based fallback would be
   wrong.

## Auth headers

Built in `HttpExecutor.applyAuth`:

| Configured fields | Header sent |
| --- | --- |
| `pat` non-empty | `Authorization: Bearer <pat>` |
| `username` + `api_token` non-empty | `Authorization: Basic <base64(user:token)>` |
| Neither | No auth header (anonymous request) |

PAT wins if both are set. Always paired with `Accept: application/json, */*;q=0.5`
and `User-Agent: jcme/0.1`.

## Cloud-ID auto-discovery

For URLs ending in `.atlassian.net` (and only those), the factory probes
`<base>/_edge/tenant_info` on first connection with a 5-second timeout. If the
response includes a `cloudId`, jcme:

1. Persists it back to the config under `auth.confluence.<url>.cloud_id`.
2. Switches subsequent REST calls to the API gateway:
   `https://api.atlassian.com/ex/confluence/<cloudId>`.

This unlocks scoped API tokens (which don't work against the regular
`*.atlassian.net` endpoint).

If `api_url` is set, the cloud-id probe is **skipped** — that's a deliberate
"this isn't standard Atlassian Cloud" signal.

## Split-host (`api_url` override)

Some corporate installs serve the HTML on one hostname and the REST API on
another (e.g. `confluence.axa.com` for HTML and `confluencews.axa.com` for the
API). Configure with:

```json
{
  "auth": {
    "confluence": {
      "https://confluence.axa.com/confluence": {
        "username": "...",
        "api_token": "...",
        "api_url": "https://confluencews.axa.com"
      }
    }
  }
}
```

When `api_url` is set, `ApiClientFactory` picks **that** URL for the HTTP
client's base, but the auth lookup still happens against the page-host URL
(so URL parsing for browser-pasted links continues to work).

## HTTP client setup

One `java.net.http.HttpClient` per `(baseUrl, ApiDetails)` combination, shared
across all worker threads (JDK guarantees thread-safety for `HttpClient.send`).

Configuration knobs from `ConnectionConfig`:

| Setting | Default | Effect |
| --- | --- | --- |
| `timeout` | `30` | Per-request timeout in seconds. Applied to both the connect and the response read. |
| `verify_ssl` | `true` | When `false`, installs a trust-all `SSLContext`. Only set if you know the certificate is fine but the JVM doesn't trust the CA. |
| `backoff_and_retry` | `true` | Enable retry loop. |
| `backoff_factor` | `2` | Exponential base — `min(2^attempt, max_backoff_seconds)` between attempts. |
| `max_backoff_seconds` | `60` | Caps each sleep. |
| `max_backoff_retries` | `5` | Total attempts = retries + 1. |
| `retry_status_codes` | `[413, 429, 502, 503, 504]` | Status codes that trigger a retry. |
| `use_v2_api` | `false` | Use `/api/v2/pages?id=...` for batched existence checks. Capped at 25 IDs/batch when off (v1 CQL limit). |
| `max_workers` | `20` | Parallel page-export workers. |

## Retry behaviour

For each request `HttpExecutor.send` runs a loop up to
`max_backoff_retries + 1` times:

1. Send the request.
2. If status `< 400`: return.
3. If status is in `retry_status_codes` and we have attempts left: sleep
   `min(backoff_factor^attempt, max_backoff_seconds)` seconds and retry.
4. Otherwise return the response (caller decides how to handle 4xx/5xx).
5. On `IOException` (network error), retry the same way.

DEBUG logs every request URL, attempt number, timeout, and elapsed time on
completion. WARN logs every retry sleep and status code that triggered it.
ERROR logs network failures after the last attempt.

## Per-base-URL client cache

`ApiClientFactory` keeps two `ConcurrentHashMap`s: one for `ConfluenceClient`
(by base URL), one for `JiraClient`. Each entry is created on first
`getConfluence(url)` / `getJira(url)` call and reused thereafter. The
`verifyAuth()` call (a single low-cost API ping) runs once per cache miss to
catch invalid credentials early.

`invalidateConfluence(url)` / `invalidateJira(url)` exist for the rare case
where credentials need to be re-loaded mid-run (e.g. after the Jira
SSO-failure detection in `JiraClient`).

## Fetcher caches

One layer above the HTTP client, [ConfluenceFetcher](../src/main/java/de/skerkewitz/jcme/fetch/ConfluenceFetcher.java)
holds typed-record caches keyed by `(baseUrl, id)`:

- `pageCache` — `Map<CacheKey<Long>, Page>`. Critical because page links cross-reference each other.
- `spaceCache` — `Map<CacheKey<String>, Space>`. Each ancestor of each page asks for the space.
- `orgCache` — `Map<CacheKey<String>, Organization>`.
- `userByUsername` / `userByKey` / `userByAccountId` — for `<a class="user-mention">`.
- `jiraIssues` — for the Jira issue macro lookup.

These caches live for the duration of the run (the lifetime of the
`ConfluenceFetcher` instance). They're not persisted — a fresh process makes
fresh API calls.

## Endpoints used

What jcme actually hits on the Confluence REST API, in order of how often:

| Method | Path | Used for |
| --- | --- | --- |
| GET | `/rest/api/content/{id}?expand=body.view,body.export_view,body.editor2,metadata.labels,metadata.properties,ancestors,version` | Fetch a full page |
| GET | `/rest/api/content/{id}/child/attachment?start=N&limit=50&expand=container.ancestors,version` | List attachments (paginated) |
| GET | `/download/...` (from `_links.download`) | Download an attachment's bytes |
| GET | `/rest/api/space/{key}?expand=homepage` | Fetch a space (resolves homepage id) |
| GET | `/rest/api/space?type=global&status=current&expand=homepage&limit=100&start=N` | List all spaces in an org (paginated) |
| GET | `/rest/api/content?spaceKey=K&title=T&type=page&expand=version` | Resolve a page by title (used when URL has no page ID) |
| GET | `/rest/api/content/search?cql=type=page+AND+ancestor=N&limit=250&expand=metadata.properties,ancestors,version` | List descendants of a page |
| GET | `<next link>` | Follow `_links.next` for v1 pagination |
| GET | `/api/v2/pages?id=A&id=B&...&limit=N` | v2 batched existence check (when `use_v2_api=true`) |
| GET | `/_edge/tenant_info` | Cloud ID auto-discovery (`*.atlassian.net` only) |
| GET | `/rest/api/user?accountId=...` | User mention resolution |
| GET | `/rest/api/2/issue/{key}` | Jira enrichment for the `<span data-macro-name="jira">` macro |

Every other Confluence URL (e.g. WebDAV, REST v2 content, etc.) is **not**
called. Adding new endpoints requires adding a method to
[ConfluenceClient.java](../src/main/java/de/skerkewitz/jcme/api/ConfluenceClient.java).

## Logging

DEBUG mode logs every HTTP method+URL with attempt count and elapsed time
on completion. INFO mode logs cache misses, auth verification, and per-page
events. See the [Logging section in the top-level README](../README.md#logging)
for how to flip levels.
