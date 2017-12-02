package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.util.Optional;

public class EagerHttpResponse<Response> extends RawHttpResponse<Response> {

    public EagerHttpResponse(RawHttpResponse<Response> response) throws IOException {
        super(response.getLibResponse().orElse(null),
                response.getRequest().orElse(null),
                response.getStartLine(), response.getHeaders(),
                response.getBody().isPresent() ? response.getBody().get().eager() : null
        );
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
