package com.athaydes.rawhttp.core;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import javax.annotation.Nullable;

import static com.athaydes.rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders;

/**
 * An eager specialization of {@link RawHttpRequest}.
 * <p>
 * Normally, an instance of this class is obtained by calling {@link RawHttpRequest#eagerly()}.
 * Doing that guarantees that the request is fully downloaded or read from its source, so that
 * it can be passed around even after the connection or stream it originates from has been closed.
 */
public class EagerHttpRequest extends RawHttpRequest {

    private EagerHttpRequest(MethodLine methodLine,
                             RawHttpHeaders headers,
                             @Nullable BodyReader bodyReader,
                             @Nullable InetAddress senderAddress) {
        super(methodLine, headers, bodyReader, senderAddress);
    }

    /**
     * Create a new {@link EagerHttpRequest} from the provided request.
     *
     * @param request raw HTTP request
     * @return an eager HTTP request from the given HTTP request.
     * @throws IOException if an error occurs while reading the request.
     */
    public static EagerHttpRequest from(RawHttpRequest request)
            throws IOException {
        if (request instanceof EagerHttpRequest) {
            return (EagerHttpRequest) request;
        }

        @Nullable EagerBodyReader bodyReader = request.getBody().isPresent() ?
                request.getBody().get().eager() :
                null;

        // headers might come from the trailing part of the HTTP message, in which case we merge them together
        RawHttpHeaders headers;
        if (bodyReader != null) {
            RawHttpHeaders trailingHeaders = bodyReader.asChunkedBodyContents()
                    .map(ChunkedBodyContents::getTrailerHeaders)
                    .orElse(emptyRawHttpHeaders());
            headers = RawHttpHeaders.Builder.newBuilder(request.getHeaders())
                    .merge(trailingHeaders)
                    .build();
        } else {
            headers = request.getHeaders();
        }

        return new EagerHttpRequest(request.getStartLine(), headers,
                request.getBody().orElse(null), request.getSenderAddress().orElse(null));
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

    @Override
    public EagerHttpRequest withHeaders(RawHttpHeaders headers) {
        return new EagerHttpRequest(getStartLine(), getHeaders().and(headers),
                getBody().orElse(null), getSenderAddress().orElse(null));
    }

    @Override
    public EagerHttpRequest withMethodLine(MethodLine methodLine) {
        return new EagerHttpRequest(methodLine, getHeaders(), getBody().orElse(null), getSenderAddress().orElse(null));
    }

}
