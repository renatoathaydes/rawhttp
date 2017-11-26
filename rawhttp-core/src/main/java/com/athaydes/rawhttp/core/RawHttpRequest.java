package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

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

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public Optional<BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

    public RawHttpRequest eagerly() throws IOException {
        if (bodyReader == null || bodyReader instanceof EagerBodyReader) {
            return this;
        } else {
            return new RawHttpRequest(methodLine, headers, bodyReader.eager());
        }
    }

    @Override
    public String toString() {
        String body = bodyReader == null ? "" : "\n\n" + bodyReader;
        return String.join("\n", methodLine.toString(),
                headers.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\n"))) + body;
    }
}
