package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class RawHttpRequest extends HttpMessage {

    private final MethodLine methodLine;

    public RawHttpRequest(MethodLine methodLine,
                          Map<String, Collection<String>> headers,
                          @Nullable BodyReader bodyReader) {
        super(headers, bodyReader);
        this.methodLine = methodLine;
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

    public EagerHttpRequest eagerly() throws IOException {
        return new EagerHttpRequest(this);
    }

    @Override
    public String toString() {
        String body = getBody().map(b -> "\r\n\r\n" + b).orElse("");
        return String.join("\r\n", methodLine.toString(),
                getHeaders().entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\r\n"))) + body;
    }
}
