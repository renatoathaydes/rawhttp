package rawhttp.core;

import rawhttp.core.body.BodyReader;
import rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A HTTP message, which can be either a request or a response.
 * <p>
 * HTTP messages consist of a start-line, a set of headers and an optional body.
 * See <a href="https://tools.ietf.org/html/rfc7230#section-3">Section 3</a>
 * of RFC-7230 for details.
 *
 * @see RawHttpRequest
 * @see RawHttpResponse
 */
public abstract class HttpMessage implements Writable {

    private final RawHttpHeaders headers;

    @Nullable
    private final BodyReader bodyReader;

    protected HttpMessage(RawHttpHeaders headers,
                          @Nullable BodyReader bodyReader) {
        this.headers = headers;
        this.bodyReader = bodyReader;
    }

    /**
     * @return the start-line of this HTTP message.
     */
    public abstract StartLine getStartLine();

    /**
     * Create a copy of this HTTP message, replacing its body with the given body.
     * <p>
     * The headers of this message will be adjusted if necessary to be consistent with the new body.
     *
     * @param body the new body
     * @return a copy of this HTTP message with the new body.
     */
    public HttpMessage withBody(@Nullable HttpMessageBody body) {
        return withBody(body, true);
    }

    /**
     * Create a copy of this HTTP message, replacing its body with the given body.
     * <p>
     * Notice that if the {@code adjustHeaders} parameters is set to false, the resulting Http message may become
     * inconsistent (e.g. if may have a Content-Length header with a value greater than 0,
     * while the message has no body!). This is allowed for cases where a HTTP server must return a response
     * containing body-related headers, even while it must not provide any actual body. Please use carefully.
     *
     * @param body          the new body
     * @param adjustHeaders whether to set or remove the appropriate headers to be consistent with the new body
     * @return a copy of this HTTP message with the new body.
     */
    public abstract HttpMessage withBody(@Nullable HttpMessageBody body, boolean adjustHeaders);

    /**
     * Helper method to allow sub-types to easily implement {@link HttpMessage#withBody(HttpMessageBody, boolean)}.
     *
     * @param body          the body or null if no body is returned
     * @param adjustHeaders whether to "adjust" headers for the new body
     * @param apply         create the Self type instance using the given headers and body
     * @param <Self>        the Self type
     * @return the result of calling {@code apply} with the headers maybe adjusted for the given body.
     */
    protected <Self> Self withBody(@Nullable HttpMessageBody body,
                                   boolean adjustHeaders,
                                   BiFunction<RawHttpHeaders, BodyReader, Self> apply) {
        RawHttpHeaders headers;
        if (body == null) {
            headers = adjustHeaders ?
                    HttpMessageBody.removeBodySpecificHeaders(getHeaders()) :
                    getHeaders();
        } else {
            headers = adjustHeaders ?
                    body.headersFrom(getHeaders()) :
                    getHeaders();
        }

        return apply.apply(headers, body == null ? null : body.toBodyReader());
    }

    /**
     * @return the headers of this HTTP message.
     */
    public RawHttpHeaders getHeaders() {
        return headers;
    }

    /**
     * Create a copy of this HTTP message, adding/replacing the provided headers.
     *
     * @param headers to add/replace
     * @return copy of this HTTP message with the provided headers
     */
    public abstract HttpMessage withHeaders(RawHttpHeaders headers);

    /**
     * Create a copy of this HTTP message, adding/replacing the provided headers.
     *
     * @param headers to add/replace
     * @param append  to append the given headers, as opposed to prepend them
     * @return copy of this HTTP message with the provided headers
     */
    public abstract HttpMessage withHeaders(RawHttpHeaders headers, boolean append);

    /**
     * @return the body of this HTTP message, if any.
     */
    public Optional<? extends BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

    /**
     * @return the String representation of this HTTP message.
     * This is exactly equivalent to the actual message bytes that would have been sent.
     */
    @Override
    public String toString() {
        return getStartLine() + "\r\n" + getHeaders() + "\r\n" +
                getBody().map(Object::toString).orElse("");
    }

    /**
     * Write this HTTP message to the given output.
     *
     * @param out to write this HTTP message to
     * @throws IOException if an error occurs while writing the message
     */
    @Override
    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, 4096);
    }

    /**
     * Write this HTTP message to the given output.
     *
     * @param out        to write this HTTP message to
     * @param bufferSize size of the buffer to use for writing
     * @throws IOException if an error occurs while writing the message
     */
    public void writeTo(OutputStream out, int bufferSize) throws IOException {
        getStartLine().writeTo(out);
        getHeaders().writeTo(out);
        Optional<? extends BodyReader> body = getBody();
        if (body.isPresent()) {
            try (BodyReader bodyReader = body.get()) {
                bodyReader.writeTo(out, bufferSize);
            }
        }
    }

}
