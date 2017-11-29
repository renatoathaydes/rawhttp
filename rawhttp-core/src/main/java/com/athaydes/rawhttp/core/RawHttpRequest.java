package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class RawHttpRequest {

    private final MethodLine methodLine;
    private final Map<String, Collection<String>> headers;

    // Nullable
    private final BodyReader bodyReader;

    public RawHttpRequest(MethodLine methodLine,
                          Map<String, Collection<String>> headers,
                          BodyReader bodyReader) {
        this.methodLine = methodLine;
        this.headers = headers;
        this.bodyReader = bodyReader;
    }

    public String getMethod() {
        return methodLine.getMethod();
    }

    public URI getUri() {
        return methodLine.getUri();
    }

    public String getHttpVersion() {
        return methodLine.getHttpVersion();
    }

    public MethodLine getMethodLine() {
        return methodLine;
    }

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public BodyReader getBody() {
        return bodyReader;
    }

    public EagerHttpRequest eagerly() throws IOException {
        return new EagerHttpRequest(this);
    }

    @Override
    public String toString() {
        String body = "\r\n\r\n" + bodyReader;
        return String.join("\r\n", methodLine.toString(),
                headers.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\r\n"))) + body;
    }
}
