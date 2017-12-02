package com.athaydes.rawhttp.core;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public abstract class HttpMessage {

    private final Map<String, Collection<String>> headers;

    @Nullable
    private final BodyReader bodyReader;

    public HttpMessage(Map<String, Collection<String>> headers,
                       @Nullable BodyReader bodyReader) {
        this.headers = headers;
        this.bodyReader = bodyReader;
    }

    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public Optional<? extends BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

}
