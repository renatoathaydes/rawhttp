package com.athaydes.rawhttp.core;

import com.athaydes.rawhttp.core.body.HttpMessageBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A HTTP message, which can be either a request or a response.
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc7230#section-3">Section 3</a>
 * of RFC-7230 for details.
 *
 * @see RawHttpRequest
 * @see RawHttpResponse
 */
public abstract class HttpMessage {

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
     * Replace the body of this HTTP message with the given body.
     * <p>
     * The headers of this message should be adjusted if necessary to be consistent with the new body.
     *
     * @param body body
     * @return a copy of this HTTP message with the new body.
     */
    public abstract HttpMessage replaceBody(HttpMessageBody body);

    /**
     * @return the headers of this HTTP message.
     */
    public RawHttpHeaders getHeaders() {
        return headers;
    }

    /**
     * @return the body of this HTTP message, if any.
     */
    public Optional<? extends BodyReader> getBody() {
        return Optional.ofNullable(bodyReader);
    }

    private String messageWithoutBody() {
        return String.join("\r\n", getStartLine().toString(), getHeaders().toString());
    }

    /**
     * @return the String representation of this HTTP message.
     * This is exactly equivalent to the actual message bytes that would have been sent.
     */
    @Override
    public String toString() {
        String body = getBody().map(Object::toString).orElse("");
        return messageWithoutBody() + body;
    }

    /**
     * Write this HTTP message to the given output.
     *
     * @param out to write this HTTP message to
     * @throws IOException if an error occurs while writing the message
     */
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
        out.write(messageWithoutBody().getBytes(StandardCharsets.US_ASCII));
        Optional<? extends BodyReader> body = getBody();
        if (body.isPresent()) {
            InputStream in = body.get().asStream();
            byte[] buffer = new byte[bufferSize];
            while (true) {
                int actuallyRead = in.read(buffer);
                if (actuallyRead < 0) {
                    break;
                }
                out.write(buffer, 0, actuallyRead);
            }
        }
    }

}
