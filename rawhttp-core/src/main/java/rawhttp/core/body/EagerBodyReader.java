package rawhttp.core.body;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.OptionalLong;

/**
 * An eager implementation of {@link BodyReader}.
 * <p>
 * Because this implementation eagerly consumes the HTTP message, it is not considered "live"
 * (i.e. it can be stored after the HTTP connection is closed).
 */
public final class EagerBodyReader extends BodyReader {

    @Nullable
    private final InputStream rawInputStream;
    private final byte[] rawBytes;

    /**
     * Create an {@link EagerBodyReader}.
     *
     * @param framedBody  the framed body
     * @param inputStream providing the body. The body is consumed immediately
     * @throws IOException if the inputStream throws
     */
    public EagerBodyReader(FramedBody framedBody,
                           @Nonnull InputStream inputStream) throws IOException {
        super(framedBody);
        this.rawInputStream = inputStream;
        this.rawBytes = framedBody.getBodyConsumer().consume(inputStream);
    }

    /**
     * Create an instance of this class from the given bytes.
     * <p>
     * The bytes are assumed to be the decoded HTTP message's body.
     *
     * @param bytes plain HTTP message's body
     */
    public EagerBodyReader(byte[] bytes) {
        super(new FramedBody.ContentLength(bytes.length));
        this.rawInputStream = null;
        this.rawBytes = bytes;
    }

    @Override
    public byte[] asRawBytes() {
        return rawBytes;
    }

    @Override
    public OptionalLong getLengthIfKnown() {
        // the eager body reader consumes the whole body, so we know it must fit into an array (of int size)
        return OptionalLong.of(rawBytes.length);
    }

    @Override
    public void close() throws IOException {
        if (rawInputStream != null) {
            rawInputStream.close();
        }
    }

    @Override
    public EagerBodyReader eager() {
        return this;
    }

    @Override
    public InputStream asRawStream() {
        return new ByteArrayInputStream(rawBytes);
    }

    /**
     * @return the body of the HTTP message in String format, using the {@link StandardCharsets#UTF_8} encoding.
     * @see #asRawString(Charset)
     */
    @Override
    public String toString() {
        return new String(rawBytes, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EagerBodyReader that = (EagerBodyReader) o;
        return Arrays.equals(rawBytes, that.rawBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rawBytes);
    }
}
