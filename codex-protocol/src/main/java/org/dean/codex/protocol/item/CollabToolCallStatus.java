package org.dean.codex.protocol.item;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CollabToolCallStatus {
    IN_PROGRESS("inProgress"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String jsonValue;

    CollabToolCallStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static CollabToolCallStatus fromJsonValue(String value) {
        if (value == null) {
            return null;
        }
        for (CollabToolCallStatus status : values()) {
            if (status.jsonValue.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown collab tool call status: " + value);
    }
}
