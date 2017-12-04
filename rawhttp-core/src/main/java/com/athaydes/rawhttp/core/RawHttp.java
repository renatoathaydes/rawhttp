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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;

public class RawHttp {

    public final RawHttpRequest parseRequest(String request) {
        try {
            return parseRequest(new ByteArrayInputStream(request.getBytes(UTF_8)));
        } catch (IOException e) {
            // IOException should be impossible
            throw new RuntimeException(e);
        }
    }

    public final RawHttpRequest parseRequest(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseRequest(stream).eagerly();
        }
    }

    public RawHttpRequest parseRequest(InputStream inputStream) throws IOException {
        List<String> metadataLines = parseMetadataLines(inputStream, InvalidHttpRequest::new);

        if (metadataLines.isEmpty()) {
            throw new InvalidHttpRequest("No content", 0);
        }

        MethodLine methodLine = parseMethodLine(metadataLines.remove(0));
        Map<String, Collection<String>> headers = parseHeaders(metadataLines, InvalidHttpRequest::new);

        // do a little cleanup to make sure the request is actually valid
        methodLine = verifyHost(methodLine, headers);
        headers = unmodifiableMap(headers);

        boolean hasBody = requestHasBody(headers);
        @Nullable BodyReader bodyReader = createBodyReader(inputStream, headers, hasBody);

        return new RawHttpRequest(methodLine, headers, bodyReader);
    }

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

    public final RawHttpResponse<Void> parseResponse(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return parseResponse(stream, null).eagerly();
        }
    }

    public final RawHttpResponse<Void> parseResponse(InputStream inputStream) throws IOException {
        return parseResponse(inputStream, null);
    }

    public RawHttpResponse<Void> parseResponse(InputStream inputStream,
                                               @Nullable MethodLine methodLine) throws IOException {
        List<String> metadataLines = parseMetadataLines(inputStream, InvalidHttpResponse::new);

        if (metadataLines.isEmpty()) {
            throw new InvalidHttpResponse("No content", 0);
        }

        StatusCodeLine statusCodeLine = parseStatusCodeLine(metadataLines.remove(0));
        Map<String, Collection<String>> headers = unmodifiableMap(parseHeaders(metadataLines, InvalidHttpResponse::new));

        boolean hasBody = responseHasBody(statusCodeLine, methodLine);
        @Nullable BodyReader bodyReader = createBodyReader(inputStream, headers, hasBody);

        return new RawHttpResponse<>(null, null, statusCodeLine, headers, bodyReader);
    }

    @Nullable
    private BodyReader createBodyReader(InputStream inputStream, Map<String, Collection<String>> headers, boolean hasBody) {
        @Nullable BodyReader bodyReader;

        if (hasBody) {
            @Nullable Long bodyLength = null;
            OptionalLong headerLength = parseContentLength(headers);
            if (headerLength.isPresent()) {
                bodyLength = headerLength.getAsLong();
            }
            BodyType bodyType = getBodyType(headers, bodyLength);
            bodyReader = new LazyBodyReader(bodyType, inputStream, bodyLength);
        } else {
            bodyReader = null;
        }
        return bodyReader;
    }

    static List<String> parseMetadataLines(InputStream inputStream,
                                           BiFunction<String, Integer, RuntimeException> createError) throws IOException {
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
                    metadataLines.add(metadataBuilder.toString());
                    if (next < 0 || wasNewLine) break;
                    metadataBuilder = new StringBuilder();
                    wasNewLine = true;
                } else {
                    inputStream.close();
                    throw createError.apply("Illegal character after return", lineNumber);
                }
            } else if (b == '\n') {
                // unexpected, but let's accept new-line without returns
                lineNumber++;
                metadataLines.add(metadataBuilder.toString());
                if (wasNewLine) break;
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

    public static BodyType getBodyType(Map<String, Collection<String>> headers,
                                       @Nullable Long bodyLength) {
        return bodyLength == null ?
                parseContentEncoding(headers).orElse(BodyType.CLOSE_TERMINATED) :
                BodyType.CONTENT_LENGTH;
    }

    public static boolean requestHasBody(Map<String, Collection<String>> headers) {
        // The presence of a message body in a request is signaled by a
        // Content-Length or Transfer-Encoding header field.  Request message
        // framing is independent of method semantics, even if the method does
        // not define any use for a message body.
        return headers.containsKey("Content-Length") || headers.containsKey("Transfer-Encoding");
    }

    public static boolean responseHasBody(StatusCodeLine statusCodeLine) {
        return responseHasBody(statusCodeLine, null);
    }

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

    private static Optional<BodyType> parseContentEncoding(Map<String, Collection<String>> headers) {
        Optional<String> encoding = last(headers.getOrDefault("Transfer-Encoding", emptyList()));
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

        try {
            return new StatusCodeLine(httpVersion, Integer.parseInt(statusCode), reason);
        } catch (NumberFormatException e) {
            throw new InvalidHttpResponse("Invalid status", 1);
        }

    }

    private static MethodLine verifyHost(MethodLine methodLine, Map<String, Collection<String>> headers) {
        Collection<String> host = headers.get("Host");
        if (host == null || host.isEmpty()) {
            if (methodLine.getUri().getHost() == null) {
                throw new InvalidHttpRequest("Host not given neither in method line nor Host header", 1);
            } else if (methodLine.getHttpVersion().equals("HTTP/1.1")) {
                // add the Host header to make sure the request is legal
                headers.put("Host", singletonList(methodLine.getUri().getHost()));
            }
            return methodLine;
        } else if (host.size() == 1) {
            return methodLine.withHost(host.iterator().next());
        } else {
            throw new InvalidHttpRequest("More than one Host header specified", 2);
        }
    }

    public static MethodLine parseMethodLine(String methodLine) {
        if (methodLine.isEmpty()) {
            throw new InvalidHttpRequest("Empty method line", 1);
        } else {
            String[] parts = methodLine.split("\\s+");
            if (parts.length == 2 || parts.length == 3) {
                String method = parts[0];
                URI uri = createUri(parts[1]);
                String version = parts.length == 3 ? parts[2] : "HTTP/1.1";
                return new MethodLine(method, uri, version);
            } else {
                throw new InvalidHttpRequest("Invalid method line", 1);
            }
        }
    }

    public static OptionalLong parseContentLength(Map<String, Collection<String>> headers) {
        Collection<String> contentLength = headers.getOrDefault("Content-Length", emptyList());
        if (contentLength.size() == 1) {
            return OptionalLong.of(Long.parseLong(contentLength.iterator().next()));
        } else {
            return OptionalLong.empty();
        }
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

    public static Map<String, Collection<String>> parseHeaders(
            List<String> lines,
            BiFunction<String, Integer, RuntimeException> createError) throws IOException {
        Map<String, Collection<String>> result = new HashMap<>();
        int lineNumber = 2;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }
            String[] parts = line.split(":", 2);
            if (parts.length != 2) {
                throw createError.apply("Invalid header", lineNumber);
            }
            result.computeIfAbsent(parts[0].trim(), (ignore) -> new ArrayList<>(3)).add(parts[1].trim());
        }

        return result;
    }

}
