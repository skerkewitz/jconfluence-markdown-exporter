package de.skerkewitz.jcme.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Top-level application configuration model. Mirrors the Python {@code ConfigModel}.
 */
public record AppConfig(
        @JsonProperty("export") ExportConfig export,
        @JsonProperty("connection_config") ConnectionConfig connectionConfig,
        @JsonProperty("auth") AuthConfig auth
) {
    public static AppConfig defaults() {
        return new AppConfig(
                ExportConfig.defaults(),
                ConnectionConfig.defaults(),
                AuthConfig.empty()
        );
    }
}
