package org.dean.codex.protocol.appserver;

import java.util.List;

public record ModelListResponse(List<ModelOption> models) {

    public ModelListResponse {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
