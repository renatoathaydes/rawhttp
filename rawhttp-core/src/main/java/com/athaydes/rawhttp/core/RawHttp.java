package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.BodyReader.BodyType;
import com.athaydes.rawhttp.core.errors.InvalidHttpRequest;
import com.athaydes.rawhttp.core.errors.InvalidHttpResponse;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The main class of the raw-http library.
 * <p>
 * Instances of this class can parse HTTP requests and responses, as well as their subsets such as
 * the start-line and headers.
 * <p>
 * To use a default instance, which is not 100% raw (it fixes up new-lines and request's Host headers, for example)
 * use the default constructor, otherwise, call {@link #RawHttp(RawHttpOptions)} with the appropriate options.
 *
 * @see RawHttpOptions
 * @see RawHttpRequest
 * @see RawHttpResponse
 * @see RawHttpHeaders
 */
public class RawHttp {

    private final RawHttpOptions options;

    /**
     * Create a new instance of {@link RawHttp} using the default {@link RawHttpOptions} instance.
     */
    public RawHttp() {
        this(RawHttpOptions.defaultInstance());
    }

    /**
     * Create a configured instance of {@link RawHttp}.
     *
     * @param options configuration options
     */
    public RawHttp(RawHttpOptions options) {
        this.options = options;
    }

    /**
     * Parses the given HTTP request.
     *
     * @param request in text form
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     */
    public final RawHttpRequest parseRequest(String request) {
        try {
            return parseRequest(new ByteArrayInputStream(request.getBytes(UTF_8)));
        } catch (IOException e) {
            // IOException should be impossible
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the HTTP request contained in the given file.
     *
     * @param file containing a HTTP request
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     * @throws IOException        if a problem occurs reading the file
     */
    public final RawHttpRequest parseRequest(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseRequest(stream).eagerly();
        }
    }

    /**
     * Parses the HTTP request produced by the given stream.
     *
     * @param inputStream producing a HTTP request
     * @return a parsed HTTP request object
     * @throws InvalidHttpRequest if the request is invalid
     * @throws IOException        if a problem occurs accessing the stream
     */
    public RawHttpRequest parseRequest(InputStream inputStream) throws IOException {
        List<String> metadataLines = parseMetadataLines(inputStream,
                InvalidHttpRequest::new,
                options.allowNewLineWithoutReturn());

        if (metadataLines.isEmpty()) {
            throw new InvalidHttpRequest("No content", 0);
        }

        MethodLine methodLine = parseMethodLine(metadataLines.remove(0));
        RawHttpHeaders.Builder headersBuilder = parseHeaders(metadataLines, InvalidHttpRequest::new);

        // do a little cleanup to make sure the request is actually valid
        methodLine = verifyHost(methodLine, headersBuilder);

        RawHttpHeaders headers = headersBuilder.build();

        boolean hasBody = requestHasBody(headers);
        @Nullable BodyReader bodyReader = createBodyReader(inputStream, headers, hasBody);

        return new RawHttpRequest(methodLine, headers, bodyReader);
    }

    /**
     * Parses the given HTTP response.
     *
     * @param response in text form
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     */
    public final RawHttpResponse<Void> parseResponse(String response) {
        try {
            return parseResponse(
                    new ByteArrayInputStream(response.getBytes(UTF_8)),
                    null);
        } catch (IOException e) {
            // IOException should be impossible
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the HTTP response contained in the given file.
     *
     * @param file containing a HTTP response
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     * @throws IOException         if a problem occurs reading the file
     */
    public final RawHttpResponse<Void> parseResponse(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseResponse(stream, null).eagerly();
        }
    }

    /**
     * Parses the HTTP response produced by the given stream.
     *
     * @param inputStream producing a HTTP response
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     * @throws IOException         if a problem occurs accessing the stream
     */
    public final RawHttpResponse<Void> parseResponse(InputStream inputStream) throws IOException {
        return parseResponse(inputStream, null);
    }

    /**
     * Parses the HTTP response produced by the given stream.
     *
     * @param inputStream producing a HTTP response
     * @param methodLine  optional {@link MethodLine} of the request which results in this response.
     *                    If provided, it is taken into consideration when deciding whether the response contains
     *                    a body. See <a href="https://tools.ietf.org/html/rfc7230#section-3.3">Section 3.3</a>
     *                    of RFC-7230 for details.
     * @return a parsed HTTP response object
     * @throws InvalidHttpResponse if the response is invalid
     * @throws IOException         if a problem occurs accessing the stream
     */
    public RawHttpResponse<Void> parseResponse(InputStream inputStream,
                                               @Nullable MethodLine methodLine) throws IOException {
        List<String> metadataLines = parseMetadataLines(inputStream,
                InvalidHttpResponse::new,
                options.allowNewLineWithoutReturn());

        if (metadataLines.isEmpty()) {
            throw new InvalidHttpResponse("No content", 0);
        }

        StatusCodeLine statusCodeLine = parseStatusCodeLine(metadataLines.remove(0));
        RawHttpHeaders headers = parseHeaders(metadataLines, InvalidHttpResponse::new).build();

        boolean hasBody = responseHasBody(statusCodeLine, methodLine);
        @Nullable BodyReader bodyReader = createBodyReader(inputStream, headers, hasBody);

        return new RawHttpResponse<>(null, null, statusCodeLine, headers, bodyReader);
    }

    @Nullable
    private BodyReader createBodyReader(InputStream inputStream, RawHttpHeaders headers, boolean hasBody) {
        @Nullable BodyReader bodyReader;

        if (hasBody) {
            @Nullable Long bodyLength = null;
            OptionalLong headerLength = parseContentLength(headers);
            if (headerLength.isPresent()) {
                bodyLength = headerLength.getAsLong();
            }
            BodyType bodyType = getBodyType(headers, bodyLength);
            bodyReader = new LazyBodyReader(bodyType, inputStream, bodyLength, options.allowNewLineWithoutReturn());
        } else {
            bodyReader = null;
        }
        return bodyReader;
    }

    static List<String> parseMetadataLines(InputStream inputStream,
                                           BiFunction<String, Integer, RuntimeException> createError,
                                           boolean allowNewLineWithoutReturn) throws IOException {
        List<String> metadataLines = new ArrayList<>();
        StringBuilder metadataBuilder = new StringBuilder();
        boolean wasNewLine = true;
        int lineNumber = 1;
        int b;
        while ((b = inputStream.read()) >= 0) {
            if (b == '\r') {
                // expect new-line
                int next = inputStream.read();
                if (next < 0 || next == '\n') {
                    lineNumber++;
                    if (wasNewLine) break;
                    metadataLines.add(metadataBuilder.toString());
                    if (next < 0) break;
                    metadataBuilder = new StringBuilder();
                    wasNewLine = true;
                } else {
                    inputStream.close();
                    throw createError.apply("Illegal character after return", lineNumber);
                }
            } else if (b == '\n') {
                if (!allowNewLineWithoutReturn) {
                    throw createError.apply("Illegal new-line character without preceding return", lineNumber);
                }

                // unexpected, but let's accept new-line without returns
                lineNumber++;
                if (wasNewLine) break;
                metadataLines.add(metadataBuilder.toString());
                metadataBuilder = new StringBuilder();
                wasNewLine = true;
            } else {
                metadataBuilder.append((char) b);
                wasNewLine = false;
            }
        }

        if (metadataBuilder.length() > 0) {
            metadataLines.add(metadataBuilder.toString());
        }

        return metadataLines;
    }

    /**
     * Get the body type of a HTTP message with the given headers.
     * <p>
     * If the value of the Content-Length header is known, it should be passed as the {@code bodyLength}
     * argument, as it is not extracted otherwise.
     *
     * @param headers    HTTP message's headers
     * @param bodyLength body length if known
     * @return the body type of the HTTP message
     */
    public static BodyType getBodyType(RawHttpHeaders headers,
                                       @Nullable Long bodyLength) {
        return bodyLength == null ?
                parseContentEncoding(headers).orElse(BodyType.CLOSE_TERMINATED) :
                BodyType.CONTENT_LENGTH;
    }

    /**
     * Determines whether a request with the given headers should have a body.
     *
     * @param headers HTTP request's headers
     * @return true if the headers indicate the request should have a body, false otherwise
     */
    public static boolean requestHasBody(RawHttpHeaders headers) {
        // The presence of a message body in a request is signaled by a
        // Content-Length or Transfer-Encoding header field.  Request message
        // framing is independent of method semantics, even if the method does
        // not define any use for a message body.
        return headers.contains("Content-Length") || headers.contains("Transfer-Encoding");
    }

    /**
     * Determines whether a response with the given status code should have a body.
     * <p>
     * This method ignores the method line of the request which produced such response. If the request
     * is known, use the {@link #responseHasBody(StatusCodeLine, MethodLine)} method instead.
     *
     * @param statusCodeLine status code of response
     * @return true if such response has a body, false otherwise
     */
    public static boolean responseHasBody(StatusCodeLine statusCodeLine) {
        return responseHasBody(statusCodeLine, null);
    }

    /**
     * Determines whether a response with the given status code should have a body.
     * <p>
     * If provided, the method line of the request which produced such response is taken into
     * consideration. See <a href="https://tools.ietf.org/html/rfc7230#section-3.3">Section 3.3</a>
     * of RFC-7230 for details.
     *
     * @param statusCodeLine status code of response
     * @param methodLine     method line of request, if any
     * @return true if such response has a body, false otherwise
     */
    public static boolean responseHasBody(StatusCodeLine statusCodeLine,
                                          @Nullable MethodLine methodLine) {
        if (methodLine != null) {
            if (methodLine.getMethod().equalsIgnoreCase("HEAD")) {
                return false; // HEAD response must never have a body
            }
            if (methodLine.getMethod().equalsIgnoreCase("CONNECT") &&
                    startsWith(2, statusCodeLine.getStatusCode())) {
                return false; // CONNECT successful means start tunelling
            }
        }

        int statusCode = statusCodeLine.getStatusCode();

        // All 1xx (Informational), 204 (No Content), and 304 (Not Modified)
        // responses do not include a message body.
        boolean hasNoBody = startsWith(1, statusCode) || statusCode == 204 || statusCode == 304;

        return !hasNoBody;
    }

    private static boolean startsWith(int firstDigit, int statusCode) {
        assert 0 < firstDigit && firstDigit < 10;
        int minCode = firstDigit * 100;
        int maxCode = minCode + 99;
        return minCode <= statusCode && statusCode <= maxCode;
    }

    private static Optional<BodyType> parseContentEncoding(RawHttpHeaders headers) {
        Optional<String> encoding = last(headers.get("Transfer-Encoding"));
        if (encoding.isPresent()) {
            if (encoding.get().equalsIgnoreCase("chunked")) {
                return Optional.of(BodyType.CHUNKED);
            } else {
                throw new IllegalArgumentException("Transfer-Encoding is not supported: " + encoding);
            }
        } else {
            return Optional.empty();
        }
    }

    private static Optional<String> last(Collection<String> items) {
        String result = null;
        for (String item : items) {
            result = item;
        }
        return Optional.ofNullable(result);
    }

    /**
     * Parses a HTTP response's status code line.
     *
     * @param line status code line
     * @return the status code line
     * @throws InvalidHttpResponse if the status code line is invalid
     */
    public static StatusCodeLine parseStatusCodeLine(String line) {
        if (line.trim().isEmpty()) {
            throw new InvalidHttpResponse("Empty status line", 1);
        }
        String[] parts = line.split("\\s+", 3);

        String httpVersion = "HTTP/1.1";
        String statusCode;
        String reason = "";

        switch (parts.length) {
            // accept just a status code
            case 1:
                statusCode = parts[0];
                break;
            case 2:
                httpVersion = parts[0];
                statusCode = parts[1];
                break;
            case 3:
                httpVersion = parts[0];
                statusCode = parts[1];
                reason = parts[2];
                break;
            default:
                // should never happen, we limit the split to 3 parts
                throw new IllegalStateException();
        }

        HttpVersion version;
        try {
            version = HttpVersion.parse(httpVersion);
        } catch (IllegalArgumentException e) {
            throw new InvalidHttpResponse("Invalid HTTP version", 1);
        }

        try {
            return new StatusCodeLine(version, Integer.parseInt(statusCode), reason);
        } catch (NumberFormatException e) {
            throw new InvalidHttpResponse("Invalid status", 1);
        }

    }

    private MethodLine verifyHost(MethodLine methodLine, RawHttpHeaders.Builder headers) {
        List<String> host = headers.build().get("Host");
        if (host.isEmpty()) {
            if (!options.insertHostHeaderIfMissing()) {
                throw new InvalidHttpRequest("Host header is missing", 1);
            } else if (methodLine.getUri().getHost() == null) {
                throw new InvalidHttpRequest("Host not given either in method line or Host header", 1);
            } else {
                // add the Host header to make sure the request is legal
                headers.with("Host", methodLine.getUri().getHost());
            }
            return methodLine;
        } else if (host.size() == 1) {
            if (methodLine.getUri().getHost() != null) {
                throw new InvalidHttpRequest("Host specified both in Host header and in method line", 1);
            }
            try {
                MethodLine newMethodLine = methodLine.withHost(host.iterator().next());
                // cleanup the host header
                headers.overwrite("Host", newMethodLine.getUri().getHost());
                return newMethodLine;
            } catch (IllegalArgumentException e) {
                int lineNumber = headers.getLineNumbers("Host").get(0);
                throw new InvalidHttpRequest("Invalid host header: " + e.getMessage(), lineNumber);
            }
        } else {
            int lineNumber = headers.getLineNumbers("Host").get(1);
            throw new InvalidHttpRequest("More than one Host header specified", lineNumber);
        }
    }

    /**
     * Parses a HTTP request's method line.
     *
     * @param methodLine method line
     * @return the method line
     * @throws InvalidHttpRequest if the method line is invalid
     */
    public static MethodLine parseMethodLine(String methodLine) {
        if (methodLine.isEmpty()) {
            throw new InvalidHttpRequest("Empty method line", 1);
        } else {
            String[] parts = methodLine.split("\\s+");
            if (parts.length == 2 || parts.length == 3) {
                String method = parts[0];
                URI uri = createUri(parts[1]);
                String version = parts.length == 3 ? parts[2] : "HTTP/1.1";
                HttpVersion httpVersion;
                try {
                    httpVersion = HttpVersion.parse(version);
                } catch (IllegalArgumentException e) {
                    throw new InvalidHttpRequest("Invalid HTTP version", 1);
                }
                return new MethodLine(method, uri, httpVersion);
            } else {
                throw new InvalidHttpRequest("Invalid method line", 1);
            }
        }
    }

    /**
     * Extracts the Content-Length header's value from the given headers, if available.
     * <p>
     * If more than one value is available, returns the first one.
     *
     * @param headers HTTP message's headers
     * @return the value of the Content-Length header, if any, or empty otherwise.
     */
    public static OptionalLong parseContentLength(RawHttpHeaders headers) {
        Optional<String> contentLength = headers.getFirst("Content-Length");
        return contentLength.map(s -> OptionalLong.of(Long.parseLong(s))).orElseGet(OptionalLong::empty);
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

    /**
     * Parses the HTTP messages' headers from the given lines.
     *
     * @param lines       header lines
     * @param createError error factory - used in case an error is encountered
     * @return modifiable {@link RawHttpHeaders.Builder}
     */
    public static RawHttpHeaders.Builder parseHeaders(
            List<String> lines,
            BiFunction<String, Integer, RuntimeException> createError) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.Builder.newBuilder();
        int lineNumber = 2;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }
            String[] parts = line.split(":\\s?", 2);
            if (parts.length != 2) {
                throw createError.apply("Invalid header", lineNumber);
            }
            builder.with(parts[0], parts[1], lineNumber);
            lineNumber++;
        }

        return builder;
    }

}
