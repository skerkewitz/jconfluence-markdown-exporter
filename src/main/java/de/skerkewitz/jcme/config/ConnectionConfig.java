package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConnectionConfig(
        @JsonProperty("backoff_and_retry") boolean backoffAndRetry,
        @JsonProperty("backoff_factor") int backoffFactor,
        @JsonProperty("max_backoff_seconds") int maxBackoffSeconds,
        @JsonProperty("max_backoff_retries") int maxBackoffRetries,
        @JsonProperty("retry_status_codes") List<Integer> retryStatusCodes,
        @JsonProperty("verify_ssl") boolean verifySsl,
        @JsonProperty("timeout") int timeout,
        @JsonProperty("use_v2_api") boolean useV2Api,
        @JsonProperty("max_workers") int maxWorkers
) {
    public static ConnectionConfig defaults() {
        return new ConnectionConfig(
                true,
                2,
                60,
                5,
                List.of(413, 429, 502, 503, 504),
                true,
                30,
                false,
                20
        );
    }

    public ConnectionConfig {
        retryStatusCodes = retryStatusCodes == null ? List.of() : List.copyOf(retryStatusCodes);
    }
}
