package com.athaydes.rawhttp.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    private final ConsumedBody consumedBody;

    EagerBodyReader(BodyType bodyType,
                    @Nonnull InputStream inputStream,
                    @Nullable Long bodyLength,
                    boolean allowNewLineWithoutReturn) throws IOException {
        super(bodyType);
        this.rawInputStream = inputStream;
        this.consumedBody = consumeBody(bodyType, inputStream, bodyLength, allowNewLineWithoutReturn);
    }

    /**
     * Create an instance of this class from the given bytes.
     * <p>
     * The bytes are assumed to be the decoded HTTP message's body.
     *
     * @param bytes plain HTTP message's body
     */
    public EagerBodyReader(byte[] bytes) {
        super(BodyType.CONTENT_LENGTH);
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
