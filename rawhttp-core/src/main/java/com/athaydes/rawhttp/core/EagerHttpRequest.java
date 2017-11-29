package com.athaydes.rawhttp.core;

import java.io.IOException;

public class EagerHttpRequest extends RawHttpRequest {

    public EagerHttpRequest(RawHttpRequest request) throws IOException {
        super(request.getMethodLine(), request.getHeaders(),
                request.getBody().eager());
    }

    @Override
    public EagerBodyReader getBody() {
        return (EagerBodyReader) super.getBody();
    }

    @Override
    public EagerHttpRequest eagerly() {
        return this;
    }
}
