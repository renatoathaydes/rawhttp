package rawhttp.core;

import rawhttp.core.body.encoding.HttpBodyEncodingRegistry;
import rawhttp.core.body.encoding.HttpMessageDecoder;
import rawhttp.core.body.encoding.ServiceLoaderHttpBodyEncodingRegistry;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

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
    private final boolean allowIllegalStartLineCharacters;
    private final boolean allowComments;
    private final HttpHeadersOptions httpHeadersOptions;
    private final HttpBodyEncodingRegistry encodingRegistry;

    private RawHttpOptions(boolean insertHostHeaderIfMissing,
                           boolean insertHttpVersionIfMissing,
                           boolean allowNewLineWithoutReturn,
                           boolean ignoreLeadingEmptyLine,
                           boolean allowIllegalStartLineCharacters,
                           boolean allowComments,
                           HttpHeadersOptions httpHeadersOptions,
                           HttpBodyEncodingRegistry encodingRegistry) {
        this.insertHostHeaderIfMissing = insertHostHeaderIfMissing;
        this.insertHttpVersionIfMissing = insertHttpVersionIfMissing;
        this.allowNewLineWithoutReturn = allowNewLineWithoutReturn;
        this.ignoreLeadingEmptyLine = ignoreLeadingEmptyLine;
        this.allowIllegalStartLineCharacters = allowIllegalStartLineCharacters;
        this.allowComments = allowComments;
        this.httpHeadersOptions = httpHeadersOptions;
        this.encodingRegistry = encodingRegistry;
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
     * @return an instance of {@link RawHttpOptions} that is as strict as the HTTP specification. It will not,
     * for example, allow LF without a CR character, or insert a Host header in a request when it's missing.
     */
    public static RawHttpOptions strict() {
        return RawHttpOptions.newBuilder()
                .doNotAllowNewLineWithoutReturn()
                .doNotInsertHostHeaderIfMissing()
                .build();
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
     * @return whether or not to allow illegal characters in the start line.
     */
    public boolean allowIllegalStartLineCharacters() {
        return allowIllegalStartLineCharacters;
    }

    /**
     * @return whether or not to allow comments. Any line before the message body starting with a '#' character
     * is considered to be a comment if this option is true.
     */
    public boolean allowComments() {
        return allowComments;
    }

    /**
     * @return options for parsing HTTP headers
     */
    public HttpHeadersOptions getHttpHeadersOptions() {
        return httpHeadersOptions;
    }

    /**
     * @return the encoding registry to use to encode/decode HTTP message bodies
     */
    public HttpBodyEncodingRegistry getEncodingRegistry() {
        return encodingRegistry;
    }

    /**
     * @return a new builder of {@link RawHttpOptions}.
     */
    public static Builder newBuilder() {
        return Builder.newBuilder();
    }

    /**
     * Options regarding HTTP headers parsing.
     */
    public static final class HttpHeadersOptions {
        private final int maxHeaderNameLength;
        private final int maxHeaderValueLength;
        private final Consumer<RawHttpHeaders> headersValidator;

        public static final HttpHeadersOptions DEFAULT = new HttpHeadersOptions(1000, 4000);

        public HttpHeadersOptions(int maxHeaderNameLength,
                                  int maxHeaderValueLength,
                                  Consumer<RawHttpHeaders> headersValidator) {
            this.maxHeaderNameLength = maxHeaderNameLength;
            this.maxHeaderValueLength = maxHeaderValueLength;
            this.headersValidator = headersValidator;
        }

        public HttpHeadersOptions(int maxHeaderNameLength, int maxHeaderValueLength) {
            this(maxHeaderNameLength, maxHeaderValueLength, ignore -> {
            });
        }

        public int getMaxHeaderNameLength() {
            return maxHeaderNameLength;
        }

        public int getMaxHeaderValueLength() {
            return maxHeaderValueLength;
        }

        public Consumer<RawHttpHeaders> getHeadersValidator() {
            return headersValidator;
        }
    }

    /**
     * Builder for {@link RawHttpOptions}.
     */
    public static final class Builder {

        private boolean insertHostHeaderIfMissing = true;
        private boolean allowNewLineWithoutReturn = true;
        private boolean ignoreLeadingEmptyLine = true;
        private boolean insertHttpVersionIfMissing = true;
        private boolean allowIllegalStartLineCharacters = false;
        private boolean allowComments = false;
        private HttpHeadersOptionsBuilder httpHeadersOptionsBuilder = new HttpHeadersOptionsBuilder();
        private HttpBodyEncodingRegistry encodingRegistry;

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
         * Configure {@link RawHttp} to be lenient when parsing the start-line (method line or status line).
         * <p>
         * This allows, for example, paths and queries in requests to contain whitespaces:
         * <p>
         * {@code GET http://example.com/path with /spaces?value=1 2 3 HTTP/1.1}
         * <p>
         * This works because RawHTTP in lenient mode can find each part of the URI using heuristics that, while not
         * being always strictly correct, will in most cases allow for correct parsing of hand-written
         * HTTP messages.
         *
         * @return this
         */
        public Builder allowIllegalStartLineCharacters() {
            this.allowIllegalStartLineCharacters = true;
            return this;
        }

        /**
         * Allow comments in HTTP messages.
         * <p>
         * Any line before the message body starting with the '#' character will be considered a comment if this option
         * is enabled.
         *
         * @return this
         */
        public Builder allowComments() {
            this.allowComments = true;
            return this;
        }

        /**
         * Get a builder of {@link HttpHeadersOptions} to use with this object.
         *
         * @return this
         */
        public HttpHeadersOptionsBuilder withHttpHeadersOptions() {
            return httpHeadersOptionsBuilder;
        }

        /**
         * Use a custom implementation of {@link HttpBodyEncodingRegistry}.
         * <p>
         * This can be used to provide {@link HttpMessageDecoder} implementations
         * using a different system than the default {@link ServiceLoaderHttpBodyEncodingRegistry} implementation.
         *
         * @param encodingRegistry the custom registry to use
         * @return this
         */
        public Builder withEncodingRegistry(HttpBodyEncodingRegistry encodingRegistry) {
            this.encodingRegistry = requireNonNull(encodingRegistry);
            return this;
        }

        /**
         * @return a configured instance of {@link RawHttpOptions}.
         * @see RawHttp#RawHttp(RawHttpOptions)
         */
        public RawHttpOptions build() {
            HttpBodyEncodingRegistry registry = encodingRegistry == null
                    ? new ServiceLoaderHttpBodyEncodingRegistry()
                    : encodingRegistry;

            return new RawHttpOptions(insertHostHeaderIfMissing, insertHttpVersionIfMissing,
                    allowNewLineWithoutReturn, ignoreLeadingEmptyLine, allowIllegalStartLineCharacters, allowComments,
                    httpHeadersOptionsBuilder.getOptions(), registry);
        }

        public class HttpHeadersOptionsBuilder {

            private HttpHeadersOptions options = HttpHeadersOptions.DEFAULT;

            /**
             * Set the maximum header name length allowed.
             *
             * @param length maximum allowed
             * @return this
             */
            public HttpHeadersOptionsBuilder withMaxHeaderNameLength(int length) {
                options = new HttpHeadersOptions(length,
                        options.maxHeaderValueLength, options.headersValidator);
                return this;
            }

            /**
             * Set the maximum header value length allowed.
             *
             * @param length maximum allowed
             * @return this
             */
            public HttpHeadersOptionsBuilder withMaxHeaderValueLength(int length) {
                options = new HttpHeadersOptions(options.maxHeaderNameLength,
                        length, options.headersValidator);
                return this;
            }

            public HttpHeadersOptionsBuilder withValidator(Consumer<RawHttpHeaders> validator) {
                requireNonNull(validator, "Validator must not be null");
                options = new HttpHeadersOptions(options.maxHeaderNameLength,
                        options.maxHeaderValueLength, validator);
                return this;
            }

            public Builder done() {
                return Builder.this;
            }

            private HttpHeadersOptions getOptions() {
                return options;
            }
        }

    }

}
