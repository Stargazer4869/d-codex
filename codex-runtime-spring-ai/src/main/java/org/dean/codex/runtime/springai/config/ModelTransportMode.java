package org.dean.codex.runtime.springai.config;

import java.util.Locale;

public enum ModelTransportMode {
    CHAT_FALLBACK("chat-fallback"),
    RESPONSES_HTTP("responses-http");

    private final String configValue;

    ModelTransportMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static ModelTransportMode from(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.isEmpty() || CHAT_FALLBACK.configValue.equals(normalized)) {
            return CHAT_FALLBACK;
        }
        if (RESPONSES_HTTP.configValue.equals(normalized)) {
            return RESPONSES_HTTP;
        }
        throw new IllegalArgumentException("Unsupported codex.model.transport-mode: "
                + value + ". Supported values: chat-fallback, responses-http");
    }
}
