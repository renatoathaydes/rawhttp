package rawhttp.core.body;

import java.io.ByteArrayInputStream;
import java.util.OptionalLong;
import javax.annotation.Nullable;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a byte array.
 */
public class BytesBody extends HttpMessageBody {

    private final byte[] bytes;

    public BytesBody(byte[] bytes) {
        this(bytes, null, new BodyDecoder());
    }

    public BytesBody(byte[] bytes,
                     @Nullable String contentType) {
        this(bytes, contentType, new BodyDecoder());
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are the given bytes.
     *
     * @param bytes
     * @param contentType
     * @param bodyDecoder
     */
    public BytesBody(byte[] bytes,
                     @Nullable String contentType,
                     BodyDecoder bodyDecoder) {
        super(contentType, bodyDecoder);
        this.bytes = bytes;
    }

    @Override
    protected OptionalLong getContentLength() {
        return OptionalLong.of(bytes.length);
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(
                new FramedBody.ContentLength(getBodyDecoder(), (long) bytes.length),
                new ByteArrayInputStream(bytes));
    }

}
