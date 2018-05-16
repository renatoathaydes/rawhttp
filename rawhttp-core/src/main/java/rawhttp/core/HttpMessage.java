package rawhttp.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import javax.annotation.Nullable;
import rawhttp.core.body.BodyReader;
import rawhttp.core.body.HttpMessageBody;

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
     * The headers of this message should be adjusted if necessary to be consistent with the new body.
     *
     * @param body body
     * @return a copy of this HTTP message with the new body.
     */
    public abstract HttpMessage withBody(HttpMessageBody body);

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
        String body = getBody().map(Object::toString).orElse("");
        return "" + getStartLine() + getHeaders() + body;
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
            BodyReader bodyReader = body.get();
            bodyReader.writeTo(out, bufferSize);
        }
    }

}
