package rawhttp.core;

import org.jetbrains.annotations.Nullable;
import rawhttp.core.body.BodyReader;
import rawhttp.core.body.HttpMessageBody;

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
    public RawHttpRequest withBody(@Nullable HttpMessageBody body) {
        return withBody(body, true);
    }

    @Override
    public RawHttpRequest withBody(@Nullable HttpMessageBody body, boolean adjustHeaders) {
        return withBody(body, adjustHeaders, (headers, bodyReader) ->
                new RawHttpRequest(requestLine, headers, bodyReader, senderAddress));

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
     * <p>
     * If the host header does not match the host in the new {@link URI}, it is modified to do so.
     *
     * @param requestLine to replace
     * @return copy of this HTTP message with the provided requestLine
     */
    public RawHttpRequest withRequestLine(RequestLine requestLine) {
        String newHost = RawHttpHeaders.hostHeaderValueFor(requestLine.getUri());
        if (newHost == null) {
            throw new IllegalArgumentException("RequestLine host must not be null");
        }
        RawHttpHeaders headers;
        if (newHost.equalsIgnoreCase(getHeaders().getFirst("Host").orElse(""))) {
            headers = getHeaders();
        } else {
            headers = RawHttpHeaders.newBuilderSkippingValidation(getHeaders())
                    .overwrite("Host", newHost)
                    .build();
        }
        return new RawHttpRequest(requestLine,
                headers,
                getBody().orElse(null),
                getSenderAddress().orElse(null));
    }

    /**
     * @return whether this request contains a {@code Expect} header with value {@code 100-continue}.
     */
    public boolean expectContinue() {
        return getHeaders().get("Expect").contains("100-continue");
    }

    /**
     * RFC-7231 defines in <a href="https://tools.ietf.org/html/rfc7231#section-4.2.1">Section 4.2.1</a>
     * that the following methods are safe, using the definition -
     * <em>Request methods are considered "safe" if their defined semantics are essentially read-only"</em>:
     * <ul>
     *     <li>GET</li>
     *     <li>HEAD</li>
     *     <li>OPTIONS</li>
     *     <li>TRACE</li>
     * </ul>
     *
     * @return true if the request method is one of the safe methods
     */
    public boolean usesSafeMethod() {
        String method = getMethod();
        return method.equals("GET") || method.equals("HEAD") ||
                method.equals("OPTIONS") || method.equals("TRACE");
    }
}
