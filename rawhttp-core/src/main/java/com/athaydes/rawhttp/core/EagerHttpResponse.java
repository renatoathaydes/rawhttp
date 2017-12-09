package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

import static com.athaydes.rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders;

public class EagerHttpResponse<Response> extends RawHttpResponse<Response> {

    private EagerHttpResponse(RawHttpResponse<Response> response,
                              RawHttpHeaders headers,
                              @Nullable EagerBodyReader bodyReader) throws IOException {
        super(response.getLibResponse().orElse(null),
                response.getRequest().orElse(null),
                response.getStartLine(),
                headers,
                bodyReader
        );
    }

    public static <Response> EagerHttpResponse<Response> from(RawHttpResponse<Response> response)
            throws IOException {
        if (response instanceof EagerHttpResponse) {
            return (EagerHttpResponse<Response>) response;
        }

        @Nullable EagerBodyReader bodyReader = response.getBody().isPresent() ?
                response.getBody().get().eager() :
                null;

        RawHttpHeaders headers;
        if (bodyReader != null) {
            RawHttpHeaders trailingHeaders = bodyReader.asChunkedBody()
                    .map(ChunkedBody::getTrailerHeaders)
                    .orElse(emptyRawHttpHeaders());
            headers = RawHttpHeaders.Builder.newBuilder(response.getHeaders())
                    .merge(trailingHeaders)
                    .build();
        } else {
            headers = response.getHeaders();
        }

        return new EagerHttpResponse<>(response, headers, bodyReader);
    }

    @Override
    public EagerHttpResponse<Response> eagerly() {
        return this;
    }

    @Override
    public Optional<EagerBodyReader> getBody() {
        Optional<? extends BodyReader> body = super.getBody();
        return body.map(b -> (EagerBodyReader) b);
    }

}
