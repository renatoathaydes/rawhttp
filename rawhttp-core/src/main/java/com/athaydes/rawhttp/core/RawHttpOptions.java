package com.athaydes.rawhttp.core;

/**
 * Options that can be used to configure an instance of {@link RawHttp}.
 *
 * @see RawHttpOptions.Builder
 */
public class RawHttpOptions {

    private static final RawHttpOptions DEFAULT_INSTANCE = Builder.newBuilder().build();

    private final boolean insertHostHeaderIfMissing;
    private final boolean allowNewLineWithoutReturn;

    private RawHttpOptions(boolean insertHostHeaderIfMissing,
                           boolean allowNewLineWithoutReturn) {
        this.insertHostHeaderIfMissing = insertHostHeaderIfMissing;
        this.allowNewLineWithoutReturn = allowNewLineWithoutReturn;
    }

    /**
     * @return the singleton, default instance of this class.
     * It uses the default values for all properties:
     * <ul>
     * <li>inserts the Host header if missing</li>
     * <li>allows new-line characters to not be prefixed with '\r'</li>
     * </ul>
     */
    public static RawHttpOptions defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * @return whether or not a Host header should be automatically inserted in
     * {@link RawHttpRequest} if missing. The host is obtained from the
     * {@link MethodLine#getUri()}, if possible.
     */
    public boolean insertHostHeaderIfMissing() {
        return insertHostHeaderIfMissing;
    }

    /**
     * @return whether or not to allow new-lines ('\n') to not be followed by '\r'.
     */
    public boolean allowNewLineWithoutReturn() {
        return allowNewLineWithoutReturn;
    }

    /**
     * Builder for {@link RawHttpOptions}.
     */
    public static class Builder {

        private boolean insertHostHeaderIfMissing = true;
        private boolean allowNewLineWithoutReturn = true;

        /**
         * @return a new builder of {@link RawHttpOptions}.
         */
        public static Builder newBuilder() {
            return new Builder();
        }

        private Builder() {
            // private
        }

        /**
         * Configure the {@link RawHttp} instance to NOT insert the Host header if missing
         * from a HTTP request.
         * <p>
         * Notice that in HTTP/1.1, requests missing a Host header are invalid.
         *
         * @return this
         */
        public Builder doNotInsertHostHeaderIfMissing() {
            this.insertHostHeaderIfMissing = false;
            return this;
        }

        /**
         * Configure the {@link RawHttp} instance to NOT allow HTTP messages to contain new-lines
         * ('\n') which are not followed by the return character ('\r').
         * <p>
         * By default, {@link RawHttp} will insert the return character if necessary to make the
         * HTTP message valid.
         *
         * @return this
         */
        public Builder doNotAllowNewLineWithoutReturn() {
            this.allowNewLineWithoutReturn = false;
            return this;
        }

        /**
         * @return a configured instance of {@link RawHttpOptions}.
         * @see RawHttp#RawHttp(RawHttpOptions)
         */
        public RawHttpOptions build() {
            return new RawHttpOptions(insertHostHeaderIfMissing, allowNewLineWithoutReturn);
        }

    }

}
