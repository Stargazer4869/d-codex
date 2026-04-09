package org.dean.codex.protocol.item;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CollabDeliveryState {
    QUEUED("queued"),
    DISPATCHED("dispatched"),
    WAITING("waiting"),
    WAKEUP("wakeup"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String jsonValue;

    CollabDeliveryState(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static CollabDeliveryState fromJsonValue(String value) {
        if (value == null) {
            return null;
        }
        for (CollabDeliveryState state : values()) {
            if (state.jsonValue.equalsIgnoreCase(value) || state.name().equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown collab delivery state: " + value);
    }
}
