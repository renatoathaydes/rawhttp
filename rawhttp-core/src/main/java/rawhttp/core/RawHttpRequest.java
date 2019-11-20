package rawhttp.core;

import rawhttp.core.body.BodyReader;
import rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;

/**
 * A HTTP Request.
 */
public class RawHttpRequest extends HttpMessage {

    private final RequestLine requestLine;

    @Nullable
    private final InetAddress senderAddress;

    public RawHttpRequest(RequestLine requestLine,
                          RawHttpHeaders headers,
                          @Nullable BodyReader bodyReader,
                          @Nullable InetAddress senderAddress) {
        super(headers, bodyReader);
        this.requestLine = requestLine;
        this.senderAddress = senderAddress;
    }

    /**
     * @return this request's method name.
     */
    public String getMethod() {
        return requestLine.getMethod();
    }

    /**
     * @return the URI in the method-line.
     */
    public URI getUri() {
        return requestLine.getUri();
    }

    @Override
    public RequestLine getStartLine() {
        return requestLine;
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
    public RawHttpRequest withBody(HttpMessageBody body) {
        return new RawHttpRequest(requestLine,
                body.headersFrom(getHeaders()),
                body.toBodyReader(),
                getSenderAddress().orElse(null));
    }

    @Override
    public RawHttpRequest withHeaders(RawHttpHeaders headers) {
        return withHeaders(headers, true);
    }

    @Override
    public RawHttpRequest withHeaders(RawHttpHeaders headers, boolean append) {
        return new RawHttpRequest(requestLine,
                append ? getHeaders().and(headers) : headers.and(getHeaders()),
                getBody().orElse(null),
                getSenderAddress().orElse(null));
    }

    /**
     * Create a copy of this HTTP request, replacing its requestLine with the provided one.
     *
     * @param requestLine to replace
     * @return copy of this HTTP message with the provided requestLine
     */
    public RawHttpRequest withRequestLine(RequestLine requestLine) {
        return new RawHttpRequest(requestLine,
                getHeaders(),
                getBody().orElse(null),
                getSenderAddress().orElse(null));
    }

    /**
     * @return whether this request contains a {@code Expect} header with value {@code 100-continue}.
     */
    public boolean expectContinue() {
        return getHeaders().get("Expect").contains("100-continue");
    }

}
