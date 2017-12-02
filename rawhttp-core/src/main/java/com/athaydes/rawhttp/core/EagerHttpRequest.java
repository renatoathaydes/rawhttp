package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.util.Optional;

public class EagerHttpRequest extends RawHttpRequest {

    public EagerHttpRequest(RawHttpRequest request) throws IOException {
        super(request.getStartLine(), request.getHeaders(),
                request.getBody().isPresent() ? request.getBody().get().eager() : null);
    }

    @Override
    public Optional<EagerBodyReader> getBody() {
        Optional<? extends BodyReader> body = super.getBody();
        return body.map(b -> (EagerBodyReader) b);
    }

    @Override
    public EagerHttpRequest eagerly() {
        return this;
    }
}
