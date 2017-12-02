package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public class RawHttpResponse<Response> extends HttpMessage {

    @Nullable
    private final Response libResponse;
    @Nullable
    private final RawHttpRequest request;

    private final StatusCodeLine statusCodeLine;

    public RawHttpResponse(@Nullable Response libResponse,
                           @Nullable RawHttpRequest request,
                           StatusCodeLine statusCodeLine,
                           Map<String, Collection<String>> headers,
                           @Nullable BodyReader bodyReader) {
        super(headers, bodyReader);
        this.libResponse = libResponse;
        this.request = request;
        this.statusCodeLine = statusCodeLine;
    }

    /**
     * @return the library-specific HTTP libResponse.
     */
    public Optional<Response> getLibResponse() {
        return Optional.ofNullable(libResponse);
    }

    public Optional<RawHttpRequest> getRequest() {
        return Optional.ofNullable(request);
    }

    public int getStatusCode() {
        return statusCodeLine.getStatusCode();
    }

    public StatusCodeLine getStatusCodeLine() {
        return statusCodeLine;
    }

    public EagerHttpResponse<Response> eagerly() throws IOException {
        return eagerly(false);
    }

    public EagerHttpResponse<Response> eagerly(boolean keepAlive) throws IOException {
        try {
            return new EagerHttpResponse<>(this);
        } finally {
            if (!keepAlive) {
                getBody().ifPresent(b -> {
                    try {
                        b.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public String toString() {
        String body = getBody().map(b -> "\r\n\r\n" + b).orElse("");
        return String.join("\r\n", statusCodeLine.toString(),
                getHeaders().entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\r\n"))) + body;
    }
}
