package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public class RawHttpResponse<Response> {

    private final Response libResponse;
    private final RawHttpRequest request;
    private final Map<String, Collection<String>> headers;
    private final BodyReader bodyReader;
    private final StatusCodeLine statusCodeLine;

    public RawHttpResponse(Response libResponse,
                           RawHttpRequest request,
                           Map<String, Collection<String>> headers,
                           BodyReader bodyReader,
                           StatusCodeLine statusCodeLine) {
        this.libResponse = libResponse;
        this.request = request;
        this.headers = headers;
        this.bodyReader = bodyReader;
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

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public BodyReader getBodyReader() {
        return bodyReader;
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
                bodyReader.close();   ;
            }
        }
    }

    @Override
    public String toString() {
        String body = bodyReader == null ? "" : "\r\n\r\n" + bodyReader;
        return String.join("\r\n", statusCodeLine.toString(),
                headers.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\r\n"))) + body;
    }
}
