package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public abstract class HttpMessage {

    private final Map<String, Collection<String>> headers;

    @Nullable
    private final BodyReader bodyReader;

    public HttpMessage(Map<String, Collection<String>> headers,
                       @Nullable BodyReader bodyReader) {
        this.headers = headers;
        this.bodyReader = bodyReader;
    }

    public abstract StartLine getStartLine();

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public Optional<? extends BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

    @Override
    public String toString() {
        String body = getBody().map(b -> "\r\n\r\n" + b).orElse("");
        return String.join("\r\n", getStartLine().toString(),
                getHeaders().entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\r\n"))) + body;
    }

}
