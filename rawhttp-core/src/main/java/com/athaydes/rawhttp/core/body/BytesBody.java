package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.BodyReader;
import com.athaydes.rawhttp.core.LazyBodyReader;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a byte array.
 */
public class BytesBody extends HttpMessageBody {

    private final byte[] bytes;
    private final String contentType;

    public BytesBody(byte[] bytes) {
        this(bytes, null);
    }

    public BytesBody(byte[] bytes,
                     @Nullable String contentType) {
        this.bytes = bytes;
        this.contentType = contentType;
    }

    @Override
    protected Optional<String> getContentType() {
        return Optional.ofNullable(contentType);
    }

    @Override
    protected long getContentLength() {
        return bytes.length;
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(BodyReader.BodyType.CONTENT_LENGTH,
                new ByteArrayInputStream(bytes),
                (long) bytes.length,
                false);
    }

}
