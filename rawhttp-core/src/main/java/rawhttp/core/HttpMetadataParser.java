package rawhttp.core;

import rawhttp.core.errors.InvalidHttpRequest;
import rawhttp.core.errors.InvalidHttpResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Parser of HTTP messages' metadata lines, i.e. start-line and header fields.
 * <p>
 * All methods that take a {@link InputStream} will close the stream immediately when
 * an error is detected. This avoids the possibility of hanged connections due to incompletely
 * consuming malformed HTTP messages.
 */
public final class HttpMetadataParser {

    private static final Pattern statusCodePattern = Pattern.compile("\\d{3}");

    private final RawHttpOptions options;

    /**
     * @return a strict HTTP metadata parser (uses {@link RawHttpOptions#strict()}.
     */
    public static HttpMetadataParser createStrictHttpMetadataParser() {
        return new HttpMetadataParser(RawHttpOptions.strict());
    }

    /**
     * Create an instance of {@link HttpMetadataParser}.
     *
     * @param options parsing options
     */
    public HttpMetadataParser(RawHttpOptions options) {
        this.options = options;
    }

    public RawHttpOptions getOptions() {
        return options;
    }

    /**
     * Parses the HTTP messages' headers from the given input stream.
     *
     * @param inputStream supplying the header fields
     * @param createError error factory - used in case an error is encountered
     * @return the {@link RawHttpHeaders}
     * @throws IOException if an error occurs while consuming the stream
     */
    public RawHttpHeaders parseHeaders(InputStream inputStream,
                                       BiFunction<String, Integer, RuntimeException> createError) throws IOException {
        RawHttpHeaders headers = buildHeaders(inputStream, createError).build();
        options.getHttpHeadersOptions().getHeadersValidator().accept(headers);
        return headers;
    }

    /**
     * Parses the HTTP messages' request-line from the given input stream.
     *
     * @param inputStream supplying the request-line
     * @return request-line
     * @throws IOException if an error occurs while consuming the stream
     */
    public RequestLine parseRequestLine(InputStream inputStream) throws IOException {
        return buildRequestLine(parseStartLine(inputStream,
                InvalidHttpRequest::new, options.ignoreLeadingEmptyLine()));
    }

    /**
     * Parses a HTTP request's request-line.
     *
     * @param statusLine the request-line
     * @return the request-line
     * @throws InvalidHttpRequest if the request-line is invalid
     */
    public RequestLine parseRequestLine(String statusLine) {
        return buildRequestLine(statusLine);
    }

    /**
     * Parses the provided query String into a {@link Map}.
     * <p>
     * This method does not verify nor performs any encoding/decoding.
     * <p>
     * Entries are separated with a {@code &} character, and each entry may be split into a key-value pair
     * with the {@code =} character as a separator. If no {@code =} character is found, the whole entry is taken
     * to be a key without value.
     *
     * @param queryString query string to parse
     * @return Map containing the query parameters
     */
    public Map<String, List<String>> parseQueryString(String queryString) {
        if (queryString.isEmpty()) {
            return new HashMap<>(1);
        }
        Map<String, List<String>> result = new HashMap<>(8);
        String[] queryStringParts = queryString.split("&");
        for (String queryStringPart : queryStringParts) {
            String[] entry = queryStringPart.split("=", 2);
            List<String> values = result.computeIfAbsent(entry[0], k -> new ArrayList<>(1));
            if (entry.length == 2) {
                values.add(entry[1]);
            }
        }
        return result;
    }

    private RequestLine buildRequestLine(String requestLine) {
        if (requestLine.isEmpty()) {
            throw new InvalidHttpRequest("No content", 0);
        }
        String[] parts = requestLine.split("\\s");
        if (parts.length == 2 || parts.length == 3) {
            String method = parts[0];
            OptionalInt illegalIndex = FieldValues.indexOfNotAllowedInTokens(method);
            if (illegalIndex.isPresent()) {
                throw new InvalidHttpRequest("Invalid method name: illegal character at index " +
                        illegalIndex.getAsInt(), 1);
            }
            URI uri = createUri(parts[1]);
            HttpVersion httpVersion = options.insertHttpVersionIfMissing()
                    ? HttpVersion.HTTP_1_1 : null;
            if (parts.length == 3) try {
                httpVersion = HttpVersion.parse(parts[2]);
            } catch (IllegalArgumentException e) {
                throw new InvalidHttpRequest("Invalid HTTP version", 1);
            }
            if (httpVersion == null) {
                throw new InvalidHttpRequest("Missing HTTP version", 1);
            }
            return new RequestLine(method, uri, httpVersion);
        } else {
            throw new InvalidHttpRequest("Invalid request line", 1);
        }
    }

    /**
     * Parses a HTTP response's status-line.
     *
     * @param inputStream providing the status-line
     * @return the status-line
     * @throws InvalidHttpResponse if the status-line is invalid
     * @throws IOException         if an error occurs while consuming the stream
     */
    public StatusLine parseStatusLine(InputStream inputStream) throws IOException {
        return buildStatusLine(parseStartLine(inputStream,
                InvalidHttpResponse::new, options.ignoreLeadingEmptyLine()));
    }

    /**
     * Parses a HTTP response's status-line.
     * <p>
     * This method does not perform validation of characters and is provided for
     * performance reasons where the status-line is known to be legal.
     * Prefer to use {@link HttpMetadataParser#parseStatusLine(InputStream)} in case the input cannot be trusted.
     *
     * @param statusLine the status-line
     * @return the status-line
     * @throws InvalidHttpResponse if the status-line is invalid
     */
    public StatusLine parseStatusLine(String statusLine) {
        return buildStatusLine(statusLine);
    }

    private StatusLine buildStatusLine(String line) {
        if (line.isEmpty()) {
            throw new InvalidHttpResponse("No content", 0);
        }

        String[] parts = line.split("\\s", 3);

        String httpVersion = null;
        String statusCode;
        String reason = "";

        if (parts.length == 1) {
            statusCode = parts[0];
        } else {
            if (parts[0].startsWith("HTTP")) {
                httpVersion = parts[0];
                statusCode = parts[1];
                if (parts.length == 3) {
                    reason = parts[2];
                }
            } else {
                statusCode = parts[0];

                // parts 1 and 2, if it's there, must be "reason"
                reason = parts[1];
                if (parts.length == 3) {
                    reason += " " + parts[2];
                }
            }
        }

        HttpVersion version;
        if (httpVersion == null) {
            if (options.insertHttpVersionIfMissing()) {
                version = HttpVersion.HTTP_1_1;
            } else {
                throw new InvalidHttpResponse("Missing HTTP version", 1);
            }
        } else {
            try {
                version = HttpVersion.parse(httpVersion);
            } catch (IllegalArgumentException e) {
                throw new InvalidHttpResponse("Invalid HTTP version", 1);
            }
        }

        if (!statusCodePattern.matcher(statusCode).matches()) {
            throw new InvalidHttpResponse("Invalid status code", 1);
        }

        return new StatusLine(version, Integer.parseInt(statusCode), reason);
    }

    private RawHttpHeaders.Builder buildHeaders(
            InputStream stream,
            BiFunction<String, Integer, RuntimeException> createError) throws IOException {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilderSkippingValidation();
        int lineNumber = 1;
        Map.Entry<String, String> header;
        while ((header = parseHeaderField(stream, lineNumber, createError)) != null) {
            builder.with(header.getKey(), header.getValue());
            lineNumber++;
        }
        return builder;
    }

    private String parseStartLine(InputStream inputStream,
                                  BiFunction<String, Integer, RuntimeException> createError,
                                  boolean skipLeadingNewLine) throws IOException {
        StringBuilder metadataBuilder = new StringBuilder();
        final boolean allowNewLineWithoutReturn = options.allowNewLineWithoutReturn();
        int b;
        while ((b = inputStream.read()) >= 0) {
            if (b == '\r') {
                // expect new-line
                int next = inputStream.read();
                if (next < 0 || next == '\n') {
                    if (skipLeadingNewLine) {
                        skipLeadingNewLine = false;
                        continue;
                    }
                    break;
                } else {
                    inputStream.close();
                    throw createError.apply("Illegal character after return", 1);
                }
            } else if (b == '\n') {
                if (skipLeadingNewLine) {
                    skipLeadingNewLine = false;
                    continue;
                }
                if (!allowNewLineWithoutReturn) {
                    inputStream.close();
                    throw createError.apply("Illegal new-line character without preceding return", 1);
                }

                // unexpected, but let's accept new-line without returns
                break;
            } else {
                char c = (char) b;
                if (c == ' ' || FieldValues.isAllowedInVCHARs(c)) {
                    metadataBuilder.append(c);
                } else {
                    throw createError.apply("Illegal character in HTTP start line", 1);
                }
            }
            skipLeadingNewLine = false;
        }

        return metadataBuilder.toString();
    }

    @Nullable
    private Map.Entry<String, String> parseHeaderField(InputStream inputStream,
                                                       int lineNumber,
                                                       BiFunction<String, Integer, RuntimeException> createError)
            throws IOException {
        int b = inputStream.read();

        if (b == '\r') {
            // expect new-line
            int next = inputStream.read();
            if (next < 0 || next == '\n') {
                return null; // end of headers stream
            }
        }

        final boolean allowNewLineWithoutReturn = options.allowNewLineWithoutReturn();

        if (b == '\n') {
            if (allowNewLineWithoutReturn) {
                return null; // end of headers stream
            } else {
                inputStream.close();
                throw createError.apply("Illegal new-line character", lineNumber);
            }
        }
        if (b < 0) {
            return null; // EOF
        }

        String headerName = "";
        boolean parsingValue = false;
        StringBuilder metadataBuilder = new StringBuilder();
        int length = 0;
        int lengthLimit = options.getHttpHeadersOptions().getMaxHeaderNameLength();

        do {
            length++;
            char c = (char) b;
            if (!parsingValue) {
                if (c == ':') {
                    headerName = metadataBuilder.toString();
                    if (headerName.isEmpty()) {
                        throw createError.apply("Header name is missing", lineNumber);
                    }
                    metadataBuilder.delete(0, headerName.length());
                    parsingValue = true;
                    length = -1;
                    lengthLimit = options.getHttpHeadersOptions().getMaxHeaderValueLength();
                } else if (c == '\n' || c == '\r') {
                    throw createError.apply("Invalid header: missing the ':' separator", lineNumber);
                } else {
                    if (length > lengthLimit) {
                        throw createError.apply("Header name is too long", lineNumber);
                    }
                    if (FieldValues.isAllowedInTokens(c)) {
                        metadataBuilder.append(c);
                    } else {
                        throw createError.apply("Illegal character in HTTP header name", lineNumber);
                    }
                }
            } else { // parsing header value
                if (c == '\r') {
                    // expect new-line
                    int next = inputStream.read();
                    if (next < 0 || next == '\n') {
                        break;
                    } else {
                        inputStream.close();
                        throw createError.apply("Illegal character after return", lineNumber);
                    }
                } else if (c == '\n') {
                    if (!allowNewLineWithoutReturn) {
                        inputStream.close();
                        throw createError.apply("Illegal new-line character without preceding return", lineNumber);
                    }

                    // unexpected, but let's accept new-line without returns
                    break;
                } else {
                    if (length > lengthLimit) {
                        throw createError.apply("Header value is too long", lineNumber);
                    }
                    if (FieldValues.isAllowedInHeaderValue(c)) {
                        metadataBuilder.append(c);
                    } else {
                        throw createError.apply("Illegal character in HTTP header value", lineNumber);
                    }
                }
            }
        } while ((b = inputStream.read()) >= 0);

        String headerValue = metadataBuilder.toString().trim();
        return new AbstractMap.SimpleEntry<>(headerName, headerValue);
    }

    private static URI createUri(String part) {
        if (!part.startsWith("http")) {
            part = "http://" + part;
        }
        URI uri;
        try {
            uri = new URI(part);
        } catch (URISyntaxException e) {
            throw new InvalidHttpRequest("Invalid URI: " + e.getMessage(), 1);
        }
        return uri;
    }
}