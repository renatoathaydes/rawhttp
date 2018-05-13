package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.errors.InvalidHttpRequest;
import com.athaydes.rawhttp.core.errors.InvalidHttpResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Parser of HTTP messages' metadata lines, i.e. start-line and header fields.
 * <p>
 * All methods that take a {@link InputStream} will close the stream immediately when
 * an error is detected. This avoids the possibility of hanged connections due to imcompletely
 * consuming malformed HTTP messages.
 */
public final class HttpMetadataParser {

    private static final Pattern statusCodePattern = Pattern.compile("\\d{3}");

    private final RawHttpOptions options;

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
     * @return modifiable {@link RawHttpHeaders.Builder}
     */
    public RawHttpHeaders.Builder parseHeaders(InputStream inputStream,
                                               BiFunction<String, Integer, RuntimeException> createError) throws IOException {
        return buildHeaders(parseHeaderLines(inputStream, createError), createError);
    }

    /**
     * Parses the HTTP messages' request-line from the given input stream.
     *
     * @param inputStream supplying the request-line
     * @return request-line
     */
    public RequestLine parseRequestLine(InputStream inputStream) throws IOException {
        return buildRequestLine(parseMetadataLine(inputStream, 1,
                InvalidHttpRequest::new, options.ignoreLeadingEmptyLine()));
    }

    /**
     * Parses a HTTP request's request-line.
     * <p>
     * This method does not perform validation of characters and is provided for
     * performance reasons where the request-line is known to be legal.
     * Prefer to use {@link this#parseRequestLine(InputStream)} in case the input cannot be trusted.
     *
     * @param statusLine the request-line
     * @return the request-line
     * @throws InvalidHttpRequest if the request-line is invalid
     */
    public RequestLine parseRequestLine(String statusLine) {
        return buildRequestLine(statusLine);
    }

    private RequestLine buildRequestLine(String requestLine) {
        if (requestLine.isEmpty()) {
            throw new InvalidHttpRequest("No content", 0);
        }
        String[] parts = requestLine.split("\\s");
        if (parts.length == 2 || parts.length == 3) {
            String method = parts[0];
            if (FieldValues.indexOfNotAllowedInTokens(method).isPresent()) {
                throw new InvalidHttpRequest("Invalid method name", 1);
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
     */
    public StatusLine parseStatusLine(InputStream inputStream) throws IOException {
        return buildStatusLine(parseMetadataLine(inputStream, 1,
                InvalidHttpResponse::new, options.ignoreLeadingEmptyLine()));
    }

    /**
     * Parses a HTTP response's status-line.
     * <p>
     * This method does not perform validation of characters and is provided for
     * performance reasons where the status-line is known to be legal.
     * Prefer to use {@link this#parseStatusLine(InputStream)} in case the input cannot be trusted.
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

        try {
            return new StatusLine(version, Integer.parseInt(statusCode), reason);
        } catch (NumberFormatException e) {
            throw new InvalidHttpResponse("Invalid status code", 1);
        }
    }

    private RawHttpHeaders.Builder buildHeaders(
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

    private List<String> parseHeaderLines(InputStream inputStream,
                                          BiFunction<String, Integer, RuntimeException> createError) throws IOException {
        List<String> metadataLines = new ArrayList<>();
        int lineNumber = 2;
        String line;
        while (true) {
            line = parseMetadataLine(inputStream, lineNumber, createError, false);
            if (line.isEmpty()) break;
            metadataLines.add(line);
            lineNumber++;
        }

        return metadataLines;
    }

    private String parseMetadataLine(InputStream inputStream,
                                     int lineNumber,
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
                    throw createError.apply("Illegal character after return", lineNumber);
                }
            } else if (b == '\n') {
                if (skipLeadingNewLine) {
                    skipLeadingNewLine = false;
                    continue;
                }
                if (!allowNewLineWithoutReturn) {
                    inputStream.close();
                    throw createError.apply("Illegal new-line character without preceding return", lineNumber);
                }

                // unexpected, but let's accept new-line without returns
                break;
            } else {
                // TODO verify character is a VCHAR
                metadataBuilder.append((char) b);
            }
            skipLeadingNewLine = false;
        }

        return metadataBuilder.toString();
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