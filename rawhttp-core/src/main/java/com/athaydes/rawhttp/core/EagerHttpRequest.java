package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.util.Optional;

/**
 * An eager specialization of {@link RawHttpRequest}.
 * <p>
 * Normally, an instance of this class is obtained by calling {@link RawHttpRequest#eagerly()}.
 * Doing that guarantees that the request is fully downloaded or read from its source, so that
 * it can be passed around even after the connection or stream it originates from has been closed.
 */
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

    /**
     * @return this eager request.
     */
    @Override
    public EagerHttpRequest eagerly() {
        return this;
    }
}
