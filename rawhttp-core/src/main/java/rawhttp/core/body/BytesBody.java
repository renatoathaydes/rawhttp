package rawhttp.core.body;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.OptionalLong;

/**
 * A simple {@link HttpMessageBody} whose contents are given by a byte array.
 */
public class BytesBody extends HttpMessageBody {

    private final byte[] bytes;

    /**
     * Create a {@link HttpMessageBody} whose contents are the given bytes.
     *
     * @param bytes contents of the body
     */
    public BytesBody(byte[] bytes) {
        this(bytes, null, new BodyDecoder());
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are the given bytes.
     *
     * @param bytes       contents of the body
     * @param contentType Content-Type of the body
     */
    public BytesBody(byte[] bytes,
                     @Nullable String contentType) {
        this(bytes, contentType, new BodyDecoder());
    }

    /**
     * Create a {@link HttpMessageBody} whose contents are the given bytes.
     * <p>
     * The body is assumed to be in encoded form and can be decoded with the provided {@link BodyDecoder}.
     *
     * @param bytes       contents of the body
     * @param contentType Content-Type of the body
     * @param bodyDecoder decoder capable of decoding the body
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
