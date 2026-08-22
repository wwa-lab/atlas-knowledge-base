package com.atlas.knowledgebase.adapters;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mutable cancellation owner; consumers receive it only as a {@link CancellationToken}. */
public final class CancellationSource implements CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<Runnable> callbacks = ConcurrentHashMap.newKeySet();

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        callbacks.forEach(Runnable::run);
        callbacks.clear();
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public Registration onCancel(Runnable callback) {
        if (callback == null) {
            return () -> {};
        }
        if (cancelled.get()) {
            callback.run();
            return () -> {};
        }
        callbacks.add(callback);
        if (cancelled.get() && callbacks.remove(callback)) {
            callback.run();
        }
        return () -> callbacks.remove(callback);
    }
}
