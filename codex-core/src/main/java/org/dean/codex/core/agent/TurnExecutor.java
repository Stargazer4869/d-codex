package org.dean.codex.core.agent;

import org.dean.codex.core.model.ModelInputItem;
import org.dean.codex.protocol.conversation.ThreadId;
import org.dean.codex.protocol.conversation.TurnId;
import org.dean.codex.protocol.event.CodexTurnResult;
import org.dean.codex.protocol.item.TurnItem;

import java.util.List;
import java.util.function.Consumer;

public interface TurnExecutor {

    CodexTurnResult executeTurn(ThreadId threadId, String input);

    default CodexTurnResult executeTurn(ThreadId threadId, String input, Consumer<TurnItem> itemConsumer) {
        return executeTurn(threadId, input);
    }

    default CodexTurnResult executeTurn(ThreadId threadId,
                                        String input,
                                        List<ModelInputItem> inputItems,
                                        Consumer<TurnItem> itemConsumer,
                                        TurnControl turnControl) {
        return executeTurn(threadId, input, itemConsumer, turnControl);
    }

    default CodexTurnResult executeTurn(ThreadId threadId,
                                        TurnId turnId,
                                        String input,
                                        List<ModelInputItem> inputItems,
                                        Consumer<TurnItem> itemConsumer,
                                        TurnControl turnControl) {
        return executeTurn(threadId, turnId, input, itemConsumer, turnControl);
    }

    default CodexTurnResult executeTurn(ThreadId threadId,
                                        String input,
                                        Consumer<TurnItem> itemConsumer,
                                        TurnControl turnControl) {
        return executeTurn(threadId, input, itemConsumer);
    }

    CodexTurnResult executeTurn(ThreadId threadId,
                                TurnId turnId,
                                String input,
                                Consumer<TurnItem> itemConsumer,
                                TurnControl turnControl);

    CodexTurnResult resumeTurn(ThreadId threadId, TurnId turnId);

    default CodexTurnResult resumeTurn(ThreadId threadId, TurnId turnId, Consumer<TurnItem> itemConsumer) {
        return resumeTurn(threadId, turnId);
    }

    default CodexTurnResult resumeTurn(ThreadId threadId,
                                       TurnId turnId,
                                       Consumer<TurnItem> itemConsumer,
                                       TurnControl turnControl) {
        return resumeTurn(threadId, turnId, itemConsumer);
    }
}
