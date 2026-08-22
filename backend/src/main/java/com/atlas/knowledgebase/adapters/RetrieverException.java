package com.atlas.knowledgebase.adapters;

/** Typed adapter failure that preserves security versus ordinary connector semantics. */
public final class RetrieverException extends RuntimeException {

    private final Retriever.Outcome outcome;

    private RetrieverException(Retriever.Outcome outcome, String message) {
        super(message);
        this.outcome = outcome;
    }

    public static RetrieverException failed(String message) {
        return new RetrieverException(Retriever.Outcome.FAILED, message);
    }

    public static RetrieverException security(String message) {
        return new RetrieverException(Retriever.Outcome.SECURITY, message);
    }

    public Retriever.Outcome outcome() {
        return outcome;
    }
}
