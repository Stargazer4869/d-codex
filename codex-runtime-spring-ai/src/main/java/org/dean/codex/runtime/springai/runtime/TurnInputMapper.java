package org.dean.codex.runtime.springai.runtime;

import org.dean.codex.core.model.InputImageItem;
import org.dean.codex.core.model.InputTextItem;
import org.dean.codex.core.model.ModelInputItem;
import org.dean.codex.core.model.ModelInputRole;
import org.dean.codex.protocol.appserver.TurnImageInputItem;
import org.dean.codex.protocol.appserver.TurnInputItem;
import org.dean.codex.protocol.appserver.TurnTextInputItem;

import java.util.ArrayList;
import java.util.List;

public final class TurnInputMapper {

    private TurnInputMapper() {
    }

    public static List<ModelInputItem> toModelInputItems(List<TurnInputItem> inputItems) {
        if (inputItems == null || inputItems.isEmpty()) {
            return List.of();
        }
        List<ModelInputItem> modelInputItems = new ArrayList<>();
        for (TurnInputItem inputItem : inputItems) {
            if (inputItem instanceof TurnTextInputItem textInputItem) {
                if (!textInputItem.text().isBlank()) {
                    modelInputItems.add(new InputTextItem(ModelInputRole.USER, textInputItem.text()));
                }
            }
            else if (inputItem instanceof TurnImageInputItem imageInputItem) {
                if (!imageInputItem.imageUrl().isBlank()) {
                    modelInputItems.add(new InputImageItem(ModelInputRole.USER, imageInputItem.imageUrl(), imageInputItem.detail()));
                }
            }
        }
        return List.copyOf(modelInputItems);
    }
}
