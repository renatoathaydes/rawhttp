package rawhttp.core;

/**
 * Options that can be used to configure an instance of {@link RawHttp}.
 *
 * @see RawHttpOptions.Builder
 */
public class RawHttpOptions {

    private static final RawHttpOptions DEFAULT_INSTANCE = Builder.newBuilder().build();

    private final boolean insertHostHeaderIfMissing;
    private final boolean insertHttpVersionIfMissing;
    private final boolean allowNewLineWithoutReturn;
    private final boolean ignoreLeadingEmptyLine;

    private RawHttpOptions(boolean insertHostHeaderIfMissing,
                           boolean insertHttpVersionIfMissing,
                           boolean allowNewLineWithoutReturn,
                           boolean ignoreLeadingEmptyLine) {
        this.insertHostHeaderIfMissing = insertHostHeaderIfMissing;
        this.insertHttpVersionIfMissing = insertHttpVersionIfMissing;
        this.allowNewLineWithoutReturn = allowNewLineWithoutReturn;
        this.ignoreLeadingEmptyLine = ignoreLeadingEmptyLine;
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
     * {@link RequestLine#getUri()}, if possible.
     */
    public boolean insertHostHeaderIfMissing() {
        return insertHostHeaderIfMissing;
    }

    /**
     * @return whether or not a HTTP version should be automatically inserted in a
     * {@link HttpMessage} if missing.
     */
    public boolean insertHttpVersionIfMissing() {
        return insertHttpVersionIfMissing;
    }

    /**
     * @return whether or not to allow new-lines ('\n') to not be followed by '\r'.
     */
    public boolean allowNewLineWithoutReturn() {
        return allowNewLineWithoutReturn;
    }

    /**
     * @return whether or not to ignore a leading empty line.
     */
    public boolean ignoreLeadingEmptyLine() {
        return ignoreLeadingEmptyLine;
    }

    /**
     * @return a new builder of {@link RawHttpOptions}.
     */
    public static Builder newBuilder() {
        return Builder.newBuilder();
    }

    /**
     * Builder for {@link RawHttpOptions}.
     */
    public static class Builder {

        private boolean insertHostHeaderIfMissing = true;
        private boolean allowNewLineWithoutReturn = true;
        private boolean ignoreLeadingEmptyLine = true;
        private boolean insertHttpVersionIfMissing = true;

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
         * Configure the {@link RawHttp} instance to NOT insert the HTTP version if missing
         * from a HTTP message.
         * <p>
         * If this option is used and a HTTP message which does not include a HTTP version is parsed, an Exception
         * will be thrown because it's illegal for HTTP messages to not include a version.
         * <p>
         * The default HTTP version is {@code HTTP/1.1}.
         *
         * @return this
         */
        public Builder doNotInsertHttpVersionIfMissing() {
            this.insertHttpVersionIfMissing = false;
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
         * Configure the {@link RawHttp} instance to NOT ignore trailing a new-line when parsing HTTP messages.
         * <p>
         * The HTTP specification recommends that HTTP message receivers
         * <a href="https://tools.ietf.org/html/rfc7230#section-3.5">ignore a leading empty-line</a> in the name of
         * robustness, hence {@link RawHttp} will do that by default.
         *
         * @return this
         */
        public Builder doNotIgnoreLeadingEmptyLine() {
            this.ignoreLeadingEmptyLine = false;
            return this;
        }

        /**
         * @return a configured instance of {@link RawHttpOptions}.
         * @see RawHttp#RawHttp(RawHttpOptions)
         */
        public RawHttpOptions build() {
            return new RawHttpOptions(insertHostHeaderIfMissing, insertHttpVersionIfMissing,
                    allowNewLineWithoutReturn, ignoreLeadingEmptyLine);
        }

    }

}
