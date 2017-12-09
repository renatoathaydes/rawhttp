package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

public class RawHttpResponse<Response> extends HttpMessage {

    @Nullable
    private final Response libResponse;
    @Nullable
    private final RawHttpRequest request;

    private final StatusCodeLine statusCodeLine;

    public RawHttpResponse(@Nullable Response libResponse,
                           @Nullable RawHttpRequest request,
                           StatusCodeLine statusCodeLine,
                           RawHttpHeaders headers,
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

    @Override
    public StatusCodeLine getStartLine() {
        return statusCodeLine;
    }

    public int getStatusCode() {
        return statusCodeLine.getStatusCode();
    }

    public EagerHttpResponse<Response> eagerly() throws IOException {
        return eagerly(false);
    }

    public EagerHttpResponse<Response> eagerly(boolean keepAlive) throws IOException {
        try {
            return EagerHttpResponse.from(this);
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
    public RawHttpResponse<Response> replaceBody(HttpMessageBody body) {
        return new RawHttpResponse<>(libResponse, request, statusCodeLine,
                body.headersFrom(getHeaders()), body.toBodyReader());
    }

}
