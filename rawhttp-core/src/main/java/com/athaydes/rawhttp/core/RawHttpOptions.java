package com.athaydes.rawhttp.core;

public class RawHttpOptions {

    private static final RawHttpOptions DEFAULT_INSTANCE = Builder.newBuilder().build();

    private final boolean insertHostHeaderIfMissing;
    private final boolean allowNewLineWithoutReturn;

    private RawHttpOptions(boolean insertHostHeaderIfMissing,
                           boolean allowNewLineWithoutReturn) {
        this.insertHostHeaderIfMissing = insertHostHeaderIfMissing;
        this.allowNewLineWithoutReturn = allowNewLineWithoutReturn;
    }

    public static RawHttpOptions defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public boolean insertHostHeaderIfMissing() {
        return insertHostHeaderIfMissing;
    }

    public boolean allowNewLineWithoutReturn() {
        return allowNewLineWithoutReturn;
    }

    public static class Builder {

        private boolean insertHostHeaderIfMissing = true;
        private boolean allowNewLineWithoutReturn = true;

        public static Builder newBuilder() {
            return new Builder();
        }

        private Builder() {
            // private
        }

        public Builder doNotInsertHostHeaderIfMissing() {
            this.insertHostHeaderIfMissing = false;
            return this;
        }

        public Builder doNotAllowNewLineWithoutReturn() {
            this.allowNewLineWithoutReturn = false;
            return this;
        }

        public RawHttpOptions build() {
            return new RawHttpOptions(insertHostHeaderIfMissing, allowNewLineWithoutReturn);
        }

    }

}
