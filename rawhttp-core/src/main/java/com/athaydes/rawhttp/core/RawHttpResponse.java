package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class RawHttpResponse<Response> {

    private final Response libResponse;
    private final RawHttpRequest request;
    private final Map<String, Collection<String>> headers;
    private final BodyReader bodyReader;
    private final int statusCode;

    public RawHttpResponse(Response libResponse,
                           RawHttpRequest request,
                           Map<String, Collection<String>> headers,
                           BodyReader bodyReader,
                           int statusCode) {
        this.libResponse = libResponse;
        this.request = request;
        this.headers = headers;
        this.bodyReader = bodyReader;
        this.statusCode = statusCode;
    }

    /**
     * @return the library-specific HTTP libResponse.
     */
    public Response getLibResponse() {
        return libResponse;
    }

    public RawHttpRequest getRequest() {
        return request;
    }

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public BodyReader getBodyReader() {
        return bodyReader;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public RawHttpResponse<Response> eagerly() throws IOException {
        if (bodyReader instanceof EagerBodyReader) {
            return this;
        } else {
            return new RawHttpResponse<>(libResponse, request, headers, bodyReader.eager(), statusCode);
        }
    }

    @Override
    public String toString() {
        String body = bodyReader == null ? "" : "\n\n" + bodyReader;
        return String.join("\n", request.getHttpVersion() + " " + statusCode,
                headers.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> entry.getKey() + ": " + v))
                        .collect(joining("\n"))) + body;
    }
}
