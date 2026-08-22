package com.atlas.knowledgebase.adapters;

import java.util.concurrent.CancellationException;

/** Read-only cooperative cancellation contract for provider and model operations. */
public interface CancellationToken {

    boolean isCancelled();

    Registration onCancel(Runnable callback);

    default void throwIfCancelled() {
        if (isCancelled()) {
            throw new CancellationException("operation cancelled");
        }
    }

    @FunctionalInterface
    interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
