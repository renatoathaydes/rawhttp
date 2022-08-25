package rawhttp.core;

import rawhttp.core.errors.InvalidHttpRequest;
import rawhttp.core.errors.InvalidHttpResponse;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import static java.net.URLDecoder.decode;

/**
 * Parser of HTTP messages' metadata lines, i.e. start-line and header fields.
 * <p>
 * All methods that take a {@link InputStream} will close the stream immediately when
 * an error is detected. This avoids the possibility of hanged connections due to incompletely
 * consuming malformed HTTP messages.
 */
public final class HttpMetadataParser {

    private static final Pattern statusCodePattern = Pattern.compile("\\d{3}");
    private static final Pattern uriWithSchemePattern = Pattern.compile("[a-zA-Z][a-zA-Z+\\-.]*://.*");

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
     * This method assumes the given query String is in its "raw" format, so that it can safely be split into its
     * constituent parts.
     * <p>
     * Entries are separated with a {@code &} character, and each entry may be split into a key-value pair
     * with the {@code =} character as a separator. If no {@code =} character is found, the whole entry is taken
     * to be a key without value.
     * <p>
     * Once broken up, the query components are decoded, then added to the returned Map.
     *
     * @param queryString query string to parse
     * @return Map containing the query parameters
     */
    public Map<String, List<String>> parseQueryString(String queryString) {
        if (queryString.isEmpty()) {
            return new HashMap<>(1);
        }
        Map<String, List<String>> result = new LinkedHashMap<>(8);
        String[] queryStringParts = queryString.split("&");
        for (String queryStringPart : queryStringParts) {
            String[] entry = queryStringPart.split("=", 2);
            List<String> values = result.computeIfAbsent(decodeFromUrl(entry[0]), k -> new ArrayList<>(1));
            if (entry.length == 2) {
                values.add(decodeFromUrl(entry[1]));
            }
        }
        return result;
    }

    private static String decodeFromUrl(String text) {
        try {
            return decode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // this is never expected as all JVMs support UTF-8
            throw new RuntimeException(e);
        }
    }

    private RequestLine buildRequestLine(String requestLine) {
        if (requestLine.isEmpty()) {
            throw new InvalidHttpRequest("No content", 0);
        }
        int firstSpace = requestLine.indexOf(' ');
        if (firstSpace <= 0) {
            throw new InvalidHttpRequest(String.format("Invalid request line: '%s'", requestLine), 1);
        }
        String method = requestLine.substring(0, firstSpace);
        OptionalInt illegalIndex = FieldValues.indexOfNotAllowedInTokens(method);
        if (illegalIndex.isPresent()) {
            throw new InvalidHttpRequest(
                    String.format("Invalid method name: illegal character at index %d: '%s'",
                            illegalIndex.getAsInt(), method), 1);
        }

        int lastSpace = requestLine.lastIndexOf(' ');
        String uriPart;
        HttpVersion httpVersion;
        if (firstSpace == lastSpace) {
            if (!options.insertHttpVersionIfMissing()) {
                throw new InvalidHttpRequest("Missing HTTP version", 1);
            }

            // assume HTTP version missing, so all we have left is a path
            uriPart = requestLine.substring(firstSpace + 1);
            httpVersion = HttpVersion.HTTP_1_1;
        } else {
            uriPart = requestLine.substring(firstSpace + 1, lastSpace);
            try {
                httpVersion = HttpVersion.parse(requestLine.substring(lastSpace + 1));
            } catch (IllegalArgumentException e) {
                throw new InvalidHttpRequest(e.getMessage(), 1);
            }
        }

        if (uriPart.trim().isEmpty()) {
            throw new InvalidHttpRequest("Missing request target", 1);
        }

        URI uri = parseUri(uriPart);

        return new RequestLine(method, uri, httpVersion);
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
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilderSkippingValidation()
                .withHeaderValuesCharset(options.getHttpHeadersOptions().getHeaderValuesCharset());

        HeaderParserState state = new HeaderParserState(new PushbackInputStream(stream));
        Map.Entry<String, String> header;
        while ((header = parseHeaderField(state, createError)) != null) {
            builder.with(header.getKey(), header.getValue());
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
    private Map.Entry<String, String> parseHeaderField(HeaderParserState state,
                                                       BiFunction<String, Integer, RuntimeException> createError)
            throws IOException {
        @Nullable
        String headerName = parseHeaderName(state, createError);

        if (headerName == null) {
            return null;
        }

        String headerValue = parseHeaderValue(state, createError);

        return new AbstractMap.SimpleEntry<>(headerName, headerValue);
    }

    private String parseHeaderName(HeaderParserState state,
                                   BiFunction<String, Integer, RuntimeException> createError)
            throws IOException {
        InputStream inputStream = state.pbStream;
        int b = inputStream.read();

        if (b == '\r') {
            // expect new-line
            int next = inputStream.read();
            if (next < 0 || next == '\n') {
                return null; // end of headers stream
            }
        } else if (b == '#' && options.allowComments()) {
            // consume the comment and continue
            while ((b = inputStream.read()) > 0) {
                if (b == '\n') {
                    b = inputStream.read();
                    break;
                }
            }
            if (b < 0) return null; // EOF
        }

        final boolean allowNewLineWithoutReturn = options.allowNewLineWithoutReturn();

        if (b == '\n') {
            if (allowNewLineWithoutReturn) {
                return null; // end of headers stream
            } else {
                inputStream.close();
                throw createError.apply("Illegal new-line character", state.lineNumber);
            }
        }
        if (b < 0) {
            return null; // EOF
        }

        String headerName = "";
        StringBuilder metadataBuilder = new StringBuilder();
        int length = 0;
        final int lengthLimit = options.getHttpHeadersOptions().getMaxHeaderNameLength();

        do {
            length++;

            char c = (char) b;
            if (c == ':') {
                headerName = metadataBuilder.toString();
                if (headerName.isEmpty()) {
                    throw createError.apply("Header name is missing", state.lineNumber);
                }
                break;
            } else if (c == '\n' || c == '\r') {
                throw createError.apply("Invalid header: missing the ':' separator", state.lineNumber);
            } else {
                if (length > lengthLimit) {
                    throw createError.apply("Header name is too long", state.lineNumber);
                }
                if (FieldValues.isAllowedInTokens(c)) {
                    metadataBuilder.append(c);
                } else {
                    throw createError.apply("Illegal character in HTTP header name", state.lineNumber);
                }
            }
        } while ((b = inputStream.read()) >= 0);

        return metadataBuilder.toString().trim();
    }

    private String parseHeaderValue(HeaderParserState state,
                                    BiFunction<String, Integer, RuntimeException> createError)
            throws IOException {
        final PushbackInputStream inputStream = state.pbStream;
        final int lengthLimit = options.getHttpHeadersOptions().getMaxHeaderValueLength();
        final boolean allowNewLineWithoutReturn = options.allowNewLineWithoutReturn();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(lengthLimit, 64));
        int length = 0;
        int b;

        while ((b = inputStream.read()) >= 0) {
            if (b == '\r') {
                // expect new-line
                int next = inputStream.read();
                if (next < 0) break;
                if (next == '\n') {
                    state.lineNumber++;

                    // support for multi-line headers
                    next = inputStream.read();
                    if (next < 0) break;
                    boolean isMultilineHeader = (next == ' ' || next == '\t');
                    // if multi-line header, continue as if nothing happened
                    if (isMultilineHeader) {
                    } else {
                        // otherwise, return the byte and finish this header
                        inputStream.unread(next);
                        break;
                    }
                } else {
                    inputStream.close();
                    throw createError.apply("Illegal character after return", state.lineNumber);
                }
            } else if (b == '\n') {
                if (!allowNewLineWithoutReturn) {
                    inputStream.close();
                    throw createError.apply("Illegal new-line character without preceding return", state.lineNumber);
                }

                // unexpected, but let's accept new-line without returns
                state.lineNumber++;
                break;
            } else {
                if (length > lengthLimit) {
                    throw createError.apply("Header value is too long", state.lineNumber);
                }

                if (FieldValues.isAllowedInHeaderValue(b)) {
                    out.write(b);
                } else {
                    throw createError.apply("Illegal character in HTTP header value", state.lineNumber);
                }
            }
            length++;
        }

        Charset charset = options.getHttpHeadersOptions().getHeaderValuesCharset();

        return new String(out.toByteArray(), charset).trim();
    }

    /**
     * Parses the given URI specification.
     * <p>
     * If no scheme is given, {@code http} is used.
     * <p>
     * If {@link RawHttpOptions#allowIllegalStartLineCharacters()} is {@code true}, then
     * this method allows illegal characters to appear in the text as long as
     * they don't interfere with splitting the URI into its separate components. This means that it's not
     * necessary to escape characters which are not used as URI component separators, considering the fact that URIs
     * are parsed using a "first-match-wins" strategy.
     * <p>
     * Otherwise, this method simply uses {@link URI#URI(String)} to parse the URI (i.e. it follows RFC-2396 strictly).
     *
     * @param uri the URI specification to parse
     * @return parsed URI
     */
    public URI parseUri(String uri) {
        String schemeUri = uriWithSchema(uri);
        try {
            if (options.allowIllegalStartLineCharacters()) {
                return parseUriLenient(schemeUri);
            } else {
                return new URI(schemeUri);
            }
        } catch (URISyntaxException e) {
            if (e.getReason().startsWith("Illegal character in ")) {
                int startIndex = schemeUri.length() - uri.length();
                int index = e.getIndex() - startIndex;
                throw new InvalidHttpRequest(
                        String.format("Invalid request target: %s at index %d: '%s'", e.getReason(), index, uri), 1);
            } else {
                throw new InvalidHttpRequest(
                        String.format("Invalid request target: %s", e.getReason()), 1);
            }
        }
    }

    private static URI parseUriLenient(String uri) throws URISyntaxException {
        final String scheme, userInfo, host, path, query, fragment;
        final int port;

        // String guaranteed to start with <scheme>://
        int schemeEndIndex = uri.indexOf(':');
        int hierPartStart = schemeEndIndex + 3;
        scheme = uri.substring(0, schemeEndIndex);

        int queryStartIndex = uri.indexOf('?', hierPartStart);
        int fragmentStartIndex = uri.indexOf('#', (queryStartIndex < 0) ? hierPartStart : queryStartIndex);
        query = (queryStartIndex < 0)
                ? null
                : uri.substring(queryStartIndex + 1, (fragmentStartIndex < 0) ? uri.length() : fragmentStartIndex);
        fragment = (fragmentStartIndex < 0)
                ? null
                : uri.substring(fragmentStartIndex + 1);

        String hierPart = uri.substring(hierPartStart,
                (query != null) ? queryStartIndex : (fragment != null) ? fragmentStartIndex : uri.length());

        int pathStart = hierPart.indexOf('/');
        path = (pathStart < 0) ? null : hierPart.substring(pathStart);
        int userInfoEnd = hierPart.indexOf('@');
        userInfo = (userInfoEnd < 0 || (path != null && userInfoEnd > pathStart)) ? null : hierPart.substring(0, userInfoEnd);
        Map.Entry<String, String> hostAndPort = parseHostAndPort(
                hierPart.substring(
                        (userInfo == null) ? 0 : userInfoEnd + 1,
                        (path == null) ? hierPart.length() : pathStart));
        host = hostAndPort.getKey();
        String portString = hostAndPort.getValue();
        try {
            port = (portString.isEmpty()) ? -1 : Integer.parseInt(portString);
        } catch (NumberFormatException e) {
            throw new InvalidHttpRequest("Invalid port: " + portString, 1);
        }

        return new URI(scheme, userInfo, host, port, path, query, fragment);
    }

    private static String uriWithSchema(String uri) {
        if (!uriWithSchemePattern.matcher(uri).matches()) {
            // no scheme was given, default to http
            uri = "http://" + uri;
        }
        return uri;
    }

    private static Map.Entry<String, String> parseHostAndPort(String text) {
        StringBuilder hostPart = new StringBuilder(text.length());
        StringBuilder portPart = new StringBuilder(5);
        boolean parsingPort = false;
        boolean parsingIP = false;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (!parsingIP && c == '[' && i == 0) {
                parsingIP = true;
            } else if (!parsingIP && c == ':') {
                parsingPort = true;
            } else if (parsingIP && c == ']') {
                parsingIP = false;
                parsingPort = true;
            } else {
                (parsingPort ? portPart : hostPart).append(c);
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(hostPart.toString(), portPart.toString());
    }

    private static final class HeaderParserState {
        final PushbackInputStream pbStream;
        int lineNumber = 1;

        public HeaderParserState(PushbackInputStream pbStream) {
            this.pbStream = pbStream;
        }
    }
}
