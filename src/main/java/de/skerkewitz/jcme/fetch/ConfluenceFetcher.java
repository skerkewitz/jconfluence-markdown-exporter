package de.skerkewitz.jcme.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import de.skerkewitz.jcme.api.ApiClientFactory;
import de.skerkewitz.jcme.api.ConfluenceClient;
import de.skerkewitz.jcme.api.ConfluenceRef;
import de.skerkewitz.jcme.api.JiraClient;
import de.skerkewitz.jcme.api.UrlParsing;
import de.skerkewitz.jcme.api.exceptions.ApiException;
import de.skerkewitz.jcme.api.exceptions.JiraAuthenticationException;
import de.skerkewitz.jcme.config.AppConfig;
import de.skerkewitz.jcme.config.ConfigStore;
import de.skerkewitz.jcme.model.Ancestor;
import de.skerkewitz.jcme.model.Attachment;
import de.skerkewitz.jcme.model.Descendant;
import de.skerkewitz.jcme.model.JiraIssue;
import de.skerkewitz.jcme.model.JsonHelpers;
import de.skerkewitz.jcme.model.Label;
import de.skerkewitz.jcme.model.Organization;
import de.skerkewitz.jcme.model.Page;
import de.skerkewitz.jcme.model.Space;
import de.skerkewitz.jcme.model.User;
import de.skerkewitz.jcme.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assembles typed domain models from Confluence/Jira REST responses.
 *
 * <p>Caching is per-(base URL, identifier). Equivalent to the {@code @lru_cache} decorations
 * on the Python {@code Page.from_id}, {@code Space.from_key}, {@code User.from_*}, and
 * {@code JiraIssue._fetch_cached} methods.
 */
public class ConfluenceFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(ConfluenceFetcher.class);

    public static final String PAGE_EXPAND =
            "body.view,body.export_view,body.editor2,metadata.labels,metadata.properties,ancestors,version";
    public static final String DESCENDANT_EXPAND = "metadata.properties,ancestors,version";
    public static final String ATTACHMENT_EXPAND = "container.ancestors,version";
    private static final int ATTACHMENT_PAGE_SIZE = 50;
    private static final int DESCENDANT_PAGE_SIZE = 250;

    private final ApiClientFactory apiFactory;
    private final ConfigStore configStore;

    private final ConcurrentHashMap<CacheKey<Long>, Page> pageCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey<String>, Space> spaceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey<String>, Organization> orgCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey<String>, User> userByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey<String>, User> userByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey<String>, User> userByAccountId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey<String>, JiraIssue> jiraIssues = new ConcurrentHashMap<>();

    public ConfluenceFetcher(ApiClientFactory apiFactory, ConfigStore configStore) {
        this.apiFactory = apiFactory;
        this.configStore = configStore;
    }

    public ApiClientFactory apiFactory() {
        return apiFactory;
    }

    // -------------------- Resolution from URLs --------------------

    public Page resolvePageFromUrl(String pageUrl) {
        String baseUrl = UrlParsing.extractBaseUrl(pageUrl);
        // Touch the client (creates/prompts on first connection)
        apiFactory.getConfluence(baseUrl);

        Optional<Long> queryId = UrlParsing.extractPageIdQueryParam(pageUrl);
        if (queryId.isPresent()) {
            return getPage(queryId.get(), baseUrl);
        }

        String relative = UrlParsing.relativePath(pageUrl, baseUrl);
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath(relative);
        if (ref.isPresent()) {
            ConfluenceRef r = ref.get();
            if (r.pageId() != null) {
                return getPage(r.pageId(), baseUrl);
            }
            if (r.spaceKey() != null && r.pageTitle() != null) {
                JsonNode page = apiFactory.getConfluence(baseUrl)
                        .getPageByTitle(r.spaceKey(), r.pageTitle(), "version");
                JsonNode first = first(page.path("results"));
                if (first != null && !first.isMissingNode()) {
                    return getPage(first.path("id").asLong(), baseUrl);
                }
            }
        }
        throw new IllegalArgumentException("Could not parse page URL " + pageUrl);
    }

    public Space resolveSpaceFromUrl(String spaceUrl) {
        String baseUrl = UrlParsing.extractBaseUrl(spaceUrl);
        apiFactory.getConfluence(baseUrl);
        String relative = UrlParsing.relativePath(spaceUrl, baseUrl);
        Optional<ConfluenceRef> ref = UrlParsing.parseConfluencePath(relative);
        if (ref.isPresent() && ref.get().spaceKey() != null) {
            return getSpace(ref.get().spaceKey(), baseUrl);
        }
        throw new IllegalArgumentException("Could not parse space URL " + spaceUrl);
    }

    public Organization resolveOrganizationFromUrl(String baseUrl) {
        return getOrganization(UrlParsing.normalizeInstanceUrl(baseUrl));
    }

    // -------------------- Cached fetches --------------------

    public Space getSpace(String spaceKey, String baseUrl) {
        return spaceCache.computeIfAbsent(new CacheKey<>(baseUrl, spaceKey), k -> {
            JsonNode data = apiFactory.getConfluence(baseUrl).getSpace(spaceKey, "homepage");
            return Space.fromJson(data, baseUrl);
        });
    }

    public Organization getOrganization(String baseUrl) {
        return orgCache.computeIfAbsent(new CacheKey<>(baseUrl, baseUrl), k -> {
            ConfluenceClient client = apiFactory.getConfluence(baseUrl);
            List<Space> spaces = new ArrayList<>();
            int start = 0;
            int pageSize = 100;
            while (true) {
                JsonNode resp = client.getAllSpaces("global", "current", "homepage", pageSize, start);
                JsonNode results = resp.path("results");
                int seen = 0;
                for (JsonNode s : results) {
                    spaces.add(Space.fromJson(s, baseUrl));
                    seen++;
                }
                if (seen < pageSize) break;
                start += pageSize;
            }
            return new Organization(baseUrl, spaces);
        });
    }

    public Page getPage(long pageId, String baseUrl) {
        return pageCache.computeIfAbsent(new CacheKey<>(baseUrl, pageId), k -> fetchPage(pageId, baseUrl));
    }

    private Page fetchPage(long pageId, String baseUrl) {
        ConfluenceClient client = apiFactory.getConfluence(baseUrl);
        JsonNode data;
        try {
            data = client.getPageById(pageId, PAGE_EXPAND);
        } catch (ApiException e) {
            if (e.isNotFound() || (e.statusCode() >= 400 && e.statusCode() < 500)) {
                LOG.warn("Could not access page id={} — treating as inaccessible", pageId);
                return Page.inaccessible(pageId, baseUrl);
            }
            throw e;
        }
        if (data == null || !data.isObject()) {
            throw new ApiException(
                    "Unexpected non-object response for page id=" + pageId + " at " + baseUrl,
                    -1, baseUrl);
        }

        Space space = getSpace(JsonHelpers.extractSpaceKey(data), baseUrl);
        List<Ancestor> ancestors = parseAncestors(data.path("ancestors"), baseUrl);
        List<Label> labels = new ArrayList<>();
        for (JsonNode l : JsonHelpers.walk(data, "metadata", "labels", "results")) {
            labels.add(Label.fromJson(l));
        }
        List<Attachment> attachments = getAttachments(pageId, baseUrl);

        return new Page(
                baseUrl,
                pageId,
                JsonHelpers.text(data, "title"),
                space,
                ancestors,
                Version.fromJson(data.path("version")),
                JsonHelpers.text(JsonHelpers.walk(data, "body", "view"), "value"),
                JsonHelpers.text(JsonHelpers.walk(data, "body", "export_view"), "value"),
                JsonHelpers.text(JsonHelpers.walk(data, "body", "editor2"), "value"),
                labels,
                attachments
        );
    }

    public List<Descendant> getDescendants(Page page) {
        ConfluenceClient client = apiFactory.getConfluence(page.baseUrl());
        List<Descendant> result = new ArrayList<>();
        try {
            JsonNode response = client.searchCql(
                    "type=page AND ancestor=" + page.id(), DESCENDANT_EXPAND, DESCENDANT_PAGE_SIZE);
            collectDescendants(response, result, page.baseUrl());
            String next = JsonHelpers.text(response.path("_links"), "next");
            while (next != null && !next.isEmpty()) {
                response = client.getRelative(next);
                collectDescendants(response, result, page.baseUrl());
                next = JsonHelpers.text(response.path("_links"), "next");
            }
        } catch (ApiException e) {
            if (e.isNotFound()) {
                LOG.warn("Content with ID {} not found (404) when fetching descendants.", page.id());
                return List.of();
            }
            throw e;
        }
        return result;
    }

    private void collectDescendants(JsonNode response, List<Descendant> result, String baseUrl) {
        for (JsonNode item : response.path("results")) {
            String spaceKey = JsonHelpers.extractSpaceKey(item);
            Space space = spaceKey.isEmpty() ? Space.empty(baseUrl) : getSpace(spaceKey, baseUrl);
            List<Ancestor> ancestors = parseAncestors(item.path("ancestors"), baseUrl);
            result.add(new Descendant(
                    baseUrl,
                    item.path("id").asLong(),
                    JsonHelpers.text(item, "title"),
                    space,
                    ancestors,
                    Version.fromJson(item.path("version"))
            ));
        }
    }

    public List<Attachment> getAttachments(long pageId, String baseUrl) {
        ConfluenceClient client = apiFactory.getConfluence(baseUrl);
        List<Attachment> result = new ArrayList<>();
        int start = 0;
        while (true) {
            JsonNode resp = client.getAttachmentsFromContent(pageId, start, ATTACHMENT_PAGE_SIZE, ATTACHMENT_EXPAND);
            int size = resp.path("size").asInt(0);
            for (JsonNode item : resp.path("results")) {
                result.add(parseAttachment(item, baseUrl));
            }
            if (size < ATTACHMENT_PAGE_SIZE) break;
            start += size;
        }
        return result;
    }

    private Attachment parseAttachment(JsonNode item, String baseUrl) {
        JsonNode container = item.path("container");
        List<Ancestor> containerAncestors = parseAncestors(container.path("ancestors"), baseUrl);
        // The Python code drops the first ancestor and appends the container itself,
        // mirroring [*ancestors, container][1:].
        List<Ancestor> ancestors = new ArrayList<>(containerAncestors.size());
        if (!containerAncestors.isEmpty()) {
            ancestors.addAll(containerAncestors.subList(1, containerAncestors.size()));
        }
        if (container != null && container.isObject() && container.has("id")) {
            ancestors.add(parseAncestor(container, baseUrl));
        }
        JsonNode extensions = item.path("extensions");
        Space space = getSpace(JsonHelpers.extractSpaceKey(item), baseUrl);
        return new Attachment(
                baseUrl,
                JsonHelpers.text(item, "id"),
                JsonHelpers.text(item, "title"),
                space,
                ancestors,
                Version.fromJson(item.path("version")),
                extensions.path("fileSize").asLong(0L),
                JsonHelpers.text(extensions, "mediaType"),
                JsonHelpers.text(extensions, "mediaTypeDescription"),
                JsonHelpers.text(extensions, "fileId"),
                JsonHelpers.text(extensions, "collectionName"),
                JsonHelpers.text(item.path("_links"), "download"),
                JsonHelpers.text(extensions, "comment")
        );
    }

    private List<Ancestor> parseAncestors(JsonNode node, String baseUrl) {
        if (node == null || !node.isArray()) return List.of();
        List<Ancestor> out = new ArrayList<>();
        Iterator<JsonNode> it = node.elements();
        // Python drops the first ancestor (typically the homepage of the space).
        if (it.hasNext()) it.next();
        while (it.hasNext()) out.add(parseAncestor(it.next(), baseUrl));
        return out;
    }

    private Ancestor parseAncestor(JsonNode node, String baseUrl) {
        Space space;
        String spaceKey = JsonHelpers.extractSpaceKey(node);
        if (!spaceKey.isEmpty()) {
            space = getSpace(spaceKey, baseUrl);
        } else {
            space = Space.empty(baseUrl);
        }
        return new Ancestor(
                baseUrl,
                node.path("id").asLong(),
                JsonHelpers.text(node, "title"),
                space,
                List.of(),
                Version.empty()
        );
    }

    // -------------------- Users + Jira --------------------

    public User getUserByUsername(String username, String baseUrl) {
        return userByUsername.computeIfAbsent(new CacheKey<>(baseUrl, username), k -> {
            JsonNode data = apiFactory.getConfluence(baseUrl).getUserByUsername(username);
            return User.fromJson(data);
        });
    }

    public User getUserByUserkey(String userkey, String baseUrl) {
        return userByKey.computeIfAbsent(new CacheKey<>(baseUrl, userkey), k -> {
            JsonNode data = apiFactory.getConfluence(baseUrl).getUserByUserkey(userkey);
            return User.fromJson(data);
        });
    }

    public User getUserByAccountId(String accountId, String baseUrl) {
        return userByAccountId.computeIfAbsent(new CacheKey<>(baseUrl, accountId), k -> {
            JsonNode data = apiFactory.getConfluence(baseUrl).getUserByAccountId(accountId);
            return User.fromJson(data);
        });
    }

    /**
     * Fetch a Jira issue by key. Returns empty when Jira enrichment is disabled
     * or when the API returns an auth failure.
     */
    public Optional<JiraIssue> getJiraIssue(String issueKey, String jiraUrl) {
        AppConfig settings = configStore.loadEffective();
        if (!settings.export().enableJiraEnrichment()) return Optional.empty();
        CacheKey<String> key = new CacheKey<>(jiraUrl, issueKey);
        JiraIssue cached = jiraIssues.get(key);
        if (cached != null) return Optional.of(cached);
        try {
            JiraClient client = apiFactory.getJira(jiraUrl);
            JsonNode data = client.getIssue(issueKey);
            JiraIssue issue = JiraIssue.fromJson(data);
            jiraIssues.put(key, issue);
            return Optional.of(issue);
        } catch (JiraAuthenticationException e) {
            apiFactory.invalidateJira(jiraUrl);
            return Optional.empty();
        } catch (ApiException e) {
            return Optional.empty();
        }
    }

    /** Mostly used in tests / for hot paths that want to seed the cache. */
    public void cachePage(Page page) {
        pageCache.put(new CacheKey<>(page.baseUrl(), page.id()), page);
    }

    private static JsonNode first(JsonNode array) {
        if (array == null || !array.isArray()) return null;
        Iterator<JsonNode> it = array.elements();
        return it.hasNext() ? it.next() : null;
    }

    /** Forces a {@link URI} parse early for fail-fast URL validation. */
    static URI safeUri(String url) {
        return URI.create(url);
    }

    private record CacheKey<T>(String baseUrl, T id) {}
}
