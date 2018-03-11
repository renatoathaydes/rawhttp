package com.athaydes.rawhttp.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * An eager implementation of {@link BodyReader}.
 * <p>
 * Because this implementation eagerly consumes the HTTP message, it is not considered "live"
 * (i.e. it can be stored after the HTTP connection is closed).
 */
public class EagerBodyReader extends BodyReader {

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
    protected OptionalLong getLengthIfKnown() {
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

    /**
     * @return the HTTP message's body as bytes.
     * Notice that this method does not decode the body, so if the body is chunked, for example,
     * the bytes will represent the chunked body, not the decoded body.
     * Use {@link #asChunkedBodyContents()} then {@link ChunkedBodyContents#getData()} to decode the body in such cases.
     */
    public byte[] asBytes() {
        return consumedBody.use(Function.identity(), ChunkedBodyContents::getData);
    }

    /**
     * @return the body of the HTTP message as a {@link ChunkedBodyContents} if the body indeed used
     * the chunked transfer coding. If the body was not chunked, this method returns an empty value.
     */
    public Optional<ChunkedBodyContents> asChunkedBodyContents() {
        return consumedBody.use(b -> Optional.empty(), Optional::of);
    }

    @Override
    public InputStream asStream() {
        return new ByteArrayInputStream(asBytes());
    }

    /**
     * Convert the HTTP message's body into a String.
     *
     * @param charset text message's charset
     * @return String representing the HTTP message's body.
     */
    public String asString(Charset charset) {
        return new String(asBytes(), charset);
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
