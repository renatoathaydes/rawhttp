package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.body.HttpMessageBody;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A HTTP Request.
 */
public class RawHttpRequest extends HttpMessage {

    private final MethodLine methodLine;

    @Nullable
    private final InetAddress senderAddress;

    public RawHttpRequest(MethodLine methodLine,
                          RawHttpHeaders headers,
                          @Nullable BodyReader bodyReader,
                          @Nullable InetAddress senderAddress) {
        super(headers, bodyReader);
        this.methodLine = methodLine;
        this.senderAddress = senderAddress;
    }

    /**
     * @return this request's method name.
     */
    public String getMethod() {
        return methodLine.getMethod();
    }

    /**
     * @return the URI in the method-line.
     */
    public URI getUri() {
        return methodLine.getUri();
    }

    @Override
    public MethodLine getStartLine() {
        return methodLine;
    }

    /**
     * @return the address of the message sender, if known.
     */
    public Optional<InetAddress> getSenderAddress() {
        return Optional.ofNullable(senderAddress);
    }

    /**
     * Ensure that this request is read eagerly, downloading the full body if necessary.
     * <p>
     * The returned object can be safely passed around after the connection used to receive
     * this request has been closed.
     *
     * @return this request, after eagerly downloading all of its contents.
     * @throws IOException if an error occurs while reading this request
     */
    public EagerHttpRequest eagerly() throws IOException {
        return EagerHttpRequest.from(this);
    }

    @Override
    public RawHttpRequest replaceBody(HttpMessageBody body) {
        return new RawHttpRequest(methodLine,
                body.headersFrom(getHeaders()),
                body.toBodyReader(),
                getSenderAddress().orElse(null));
    }

    @Override
    public RawHttpRequest withHeaders(RawHttpHeaders headers) {
        return new RawHttpRequest(methodLine,
                getHeaders().and(headers),
                getBody().orElse(null),
                getSenderAddress().orElse(null));
    }

}
