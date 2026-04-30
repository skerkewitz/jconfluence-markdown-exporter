package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExportConfig(
        @JsonProperty("log_level") String logLevel,
        @JsonProperty("output_path") String outputPath,
        @JsonProperty("page_href") String pageHref,
        @JsonProperty("page_path") String pagePath,
        @JsonProperty("attachment_href") String attachmentHref,
        @JsonProperty("attachment_path") String attachmentPath,
        @JsonProperty("attachment_export_all") boolean attachmentExportAll,
        @JsonProperty("page_breadcrumbs") boolean pageBreadcrumbs,
        @JsonProperty("page_properties_as_front_matter") boolean pagePropertiesAsFrontMatter,
        @JsonProperty("filename_encoding") String filenameEncoding,
        @JsonProperty("filename_length") int filenameLength,
        @JsonProperty("filename_lowercase") boolean filenameLowercase,
        @JsonProperty("include_document_title") boolean includeDocumentTitle,
        @JsonProperty("enable_jira_enrichment") boolean enableJiraEnrichment,
        @JsonProperty("skip_unchanged") boolean skipUnchanged,
        @JsonProperty("cleanup_stale") boolean cleanupStale,
        @JsonProperty("lockfile_name") String lockfileName,
        @JsonProperty("existence_check_batch_size") int existenceCheckBatchSize
) {
    public static final String DEFAULT_FILENAME_ENCODING =
            "\"<\":\"_\",\">\":\"_\",\":\":\"_\",\"\\\"\":\"_\",\"/\":\"_\",\"\\\\\":\"_\","
                    + "\"|\":\"_\",\"?\":\"_\",\"*\":\"_\",\"\\u0000\":\"_\",\"[\":\"_\",\"]\":\"_\","
                    + "\"'\":\"_\",\"\\u2019\":\"_\",\"\\u00b4\":\"_\",\"`\":\"_\"";

    public static ExportConfig defaults() {
        return new ExportConfig(
                "INFO",
                "",
                "relative",
                "{space_name}/{homepage_title}/{ancestor_titles}/{page_title}.md",
                "relative",
                "{space_name}/attachments/{attachment_file_id}{attachment_extension}",
                false,
                true,
                true,
                DEFAULT_FILENAME_ENCODING,
                255,
                false,
                true,
                true,
                true,
                true,
                "confluence-lock.json",
                250
        );
    }
}
