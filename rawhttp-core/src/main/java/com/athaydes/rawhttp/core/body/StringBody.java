package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.BodyReader;
import com.athaydes.rawhttp.core.LazyBodyReader;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a String.
 */
public class StringBody extends HttpMessageBody {

    private final byte[] body;
    private final Charset charset;

    @Nullable
    private final String contentType;

    public StringBody(String body) {
        this(body, null);
    }

    public StringBody(String body,
                      @Nullable String contentType) {
        this(body, contentType, StandardCharsets.UTF_8);
    }

    public StringBody(String body,
                      @Nullable String contentType,
                      Charset charset) {
        this.body = body.getBytes(charset);
        this.contentType = contentType;
        this.charset = charset;
    }

    @Override
    public Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    public long getContentLength() {
        return body.length;
    }

    /**
     * @return the charset of this HTTP message's body.
     */
    public Charset getCharset() {
        return charset;
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(BodyReader.BodyType.CONTENT_LENGTH,
                new ByteArrayInputStream(body),
                (long) body.length,
                false);
    }

}
