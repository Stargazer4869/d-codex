package org.dean.codex.core.agent;

import org.dean.codex.core.model.ModelInputItem;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.event.CodexTurnResult;
import org.dean.codex.protocol.item.TurnItem;

import java.util.List;
import java.util.function.Consumer;

public interface CodexAgent {

    CodexTurnResult handleTurn(ThreadId threadId, TurnId turnId, String input);

    default CodexTurnResult handleTurn(ThreadId threadId,
                                       TurnId turnId,
                                       String input,
                                       Consumer<TurnItem> itemConsumer) {
        return handleTurn(threadId, turnId, input);
    }

    default CodexTurnResult handleTurn(ThreadId threadId,
                                       TurnId turnId,
                                       String input,
                                       List<ModelInputItem> inputItems,
                                       Consumer<TurnItem> itemConsumer) {
        return handleTurn(threadId, turnId, input, itemConsumer);
    }

    default CodexTurnResult handleTurn(ThreadId threadId,
                                       TurnId turnId,
                                       String input,
                                       List<ModelInputItem> inputItems,
                                       Consumer<TurnItem> itemConsumer,
                                       TurnControl turnControl) {
        return handleTurn(threadId, turnId, input, itemConsumer, turnControl);
    }

    default CodexTurnResult handleTurn(ThreadId threadId,
                                       TurnId turnId,
                                       String input,
                                       Consumer<TurnItem> itemConsumer,
                                       TurnControl turnControl) {
        return handleTurn(threadId, turnId, input, itemConsumer);
    }
}
