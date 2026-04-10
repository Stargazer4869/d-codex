package org.dean.codex.runtime.springai.prompt;

public interface ToolObservationReducer {

    String reduce(String actionName, String observation);
}
