package com.athaydes.rawhttp.core;

import java.io.IOException;

public class EagerHttpResponse<Response> extends RawHttpResponse<Response> {

    public EagerHttpResponse(RawHttpResponse<Response> response) throws IOException {
        super(response.getLibResponse().orElse(null),
                response.getRequest().orElse(null),
                response.getHeaders(),
                response.getBodyReader().eager(),
                response.getStatusCodeLine());
    }

    @Override
    public EagerHttpResponse<Response> eagerly() {
        return this;
    }

    @Override
    public EagerBodyReader getBodyReader() {
        return (EagerBodyReader) super.getBodyReader();
    }
}
