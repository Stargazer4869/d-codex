package org.dean.codex.core.model;

import java.util.function.Consumer;

public interface ResponsesModelClient {

    default ModelResponse complete(ModelRequest request) {
        return complete(request, null);
    }

    ModelResponse complete(ModelRequest request, Consumer<ModelOutputItem> outputItemConsumer);
}
