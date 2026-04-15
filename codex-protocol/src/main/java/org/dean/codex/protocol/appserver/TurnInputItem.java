package org.dean.codex.protocol.appserver;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TurnTextInputItem.class, name = "text"),
        @JsonSubTypes.Type(value = TurnImageInputItem.class, name = "image")
})
public sealed interface TurnInputItem permits TurnTextInputItem, TurnImageInputItem {
}
