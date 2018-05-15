package rawhttp.core.body;

import java.io.ByteArrayInputStream;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import rawhttp.core.BodyReader;
import rawhttp.core.LazyBodyReader;

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
    protected OptionalLong getContentLength() {
        return OptionalLong.of(bytes.length);
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(new BodyReader.BodyType.ContentLength((long) bytes.length),
                null,
                new ByteArrayInputStream(bytes));
    }

}
