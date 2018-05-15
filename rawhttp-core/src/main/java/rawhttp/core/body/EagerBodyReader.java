package rawhttp.core.body;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import rawhttp.core.HttpMetadataParser;

/**
 * An eager implementation of {@link BodyReader}.
 * <p>
 * Because this implementation eagerly consumes the HTTP message, it is not considered "live"
 * (i.e. it can be stored after the HTTP connection is closed).
 */
public final class EagerBodyReader extends BodyReader {

    @Nullable
    private final InputStream rawInputStream;

    private final ConsumedBody consumedBody;

    /**
     * Create an {@link EagerBodyReader}.
     *
     * @param bodyType       body type
     * @param metadataParser HTTP metadata parser
     * @param inputStream    providing the body. The body is consumed immediately
     * @throws IOException if the inputStream throws
     */
    public EagerBodyReader(BodyType bodyType,
                           @Nullable HttpMetadataParser metadataParser,
                           @Nonnull InputStream inputStream) throws IOException {
        super(bodyType, metadataParser);
        this.rawInputStream = inputStream;
        this.consumedBody = consumeBody(bodyType, inputStream);
    }

    /**
     * Create an instance of this class from the given bytes.
     * <p>
     * The bytes are assumed to be the decoded HTTP message's body.
     *
     * @param bytes          plain HTTP message's body
     * @param metadataParser HTTP metadata parser
     */
    public EagerBodyReader(byte[] bytes, @Nullable HttpMetadataParser metadataParser) {
        super(new BodyType.ContentLength(bytes.length), metadataParser);
        this.rawInputStream = null;
        this.consumedBody = new ConsumedBody(bytes);
    }

    @Override
    protected ConsumedBody getConsumedBody() {
        return consumedBody;
    }

    @Override
    public OptionalLong getLengthIfKnown() {
        // the eager body reader consumes the whole body, so we know it must fit into an array (of int size)
        return OptionalLong.of(consumedBody.use(
                b -> (long) b.length,
                c -> (long) c.getData().length));
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
    public InputStream asStream() {
        return new ByteArrayInputStream(asBytes());
    }

    /**
     * @return the body of the HTTP message in String format, using the {@link StandardCharsets#UTF_8} encoding.
     * @see #asString(Charset)
     */
    @Override
    public String toString() {
        return asString(StandardCharsets.UTF_8);
    }

}
