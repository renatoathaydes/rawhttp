package com.athaydes.rawhttp.core.body;

import com.athaydes.rawhttp.core.BodyReader;
import com.athaydes.rawhttp.core.LazyBodyReader;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a byte array.
 */
public class BytesBody extends HttpMessageBody {

    private final byte[] bytes;

    public BytesBody(byte[] bytes) {
        this(bytes, null);
    }

    public BytesBody(byte[] bytes,
                     @Nullable String contentType) {
        super(contentType);
        this.bytes = bytes;
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
