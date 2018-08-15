package rawhttp.core.body;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lazy implementation of {@link BodyReader}.
 * <p>
 * Instances of this class are "live", i.e. they should only be used while the HTTP connection is live.
 * <p>
 * Notice also that reading the messaage body via any of the methods of this reader will cause this reader
 * to become "consumed". After this reader has been "consumed", the methods which would otherwise consume
 * the stream associated with this reader will throw an {@link IllegalStateException} if called. Doing this
 * avoids reading data accidentally, which may cause errors or a hanging connection. An exception
 * to this is the {@link #asRawStream()} method - see the Javadocs for this method for details.
 *
 * @see #eager()
 */
public final class LazyBodyReader extends BodyReader {

    private final AtomicBoolean isConsumed = new AtomicBoolean(false);
    private final InputStream inputStream;

    public LazyBodyReader(FramedBody framedBody,
                          InputStream inputStream) {
        super(framedBody);
        this.inputStream = inputStream;
    }

    @Override
    public OptionalLong getLengthIfKnown() {
        try {
            return getFramedBody().use(
                    cl -> OptionalLong.of(cl.getBodyLength()),
                    chunked -> OptionalLong.empty(),
                    ct -> OptionalLong.empty());
        } catch (IOException e) {
            return OptionalLong.empty();
        }
    }

    @Override
    public void writeTo(OutputStream out, int bufferSize) throws IOException {
        markConsumed();
        super.writeTo(out, bufferSize);
    }

    @Override
    public byte[] asRawBytes() throws IOException {
        markConsumed();
        return super.asRawBytes();
    }

    @Override
    public Optional<ChunkedBodyContents> asChunkedBodyContents() throws IOException {
        markConsumed();
        return super.asChunkedBodyContents();
    }

    @Override
    public EagerBodyReader eager() throws IOException {
        markConsumed();
        try {
            return new EagerBodyReader(getFramedBody(), inputStream);
        } catch (IOException e) {
            // error while trying to read message body, we cannot keep the connection alive
            try {
                inputStream.close();
            } catch (IOException e2) {
                // ignore
            }

            throw e;
        }
    }

    /**
     * Get the raw {@link InputStream} associated with this reader.
     * <p>
     * The reader is not marked as "consumed" after calling this method, so if any other
     * method of this reader is called after that, it will be assumed that the input stream
     * has not been used and an attempt to read the HTTP message will be made as usual.
     * That can cause errors or even a hanging connection if the stream was utilized outside
     * of this reader!
     *
     * @return the stream which may produce the HTTP message body.
     * Notice that the stream may be closed if this {@link BodyReader} is closed.
     */
    @Override
    public InputStream asRawStream() {
        return inputStream;
    }

    @Override
    public void close() throws IOException {
        isConsumed.set(true);
        inputStream.close();
    }

    private void markConsumed() {
        if (!isConsumed.compareAndSet(false, true)) {
            throw new IllegalStateException("The HTTP message body has already been consumed. " +
                    "Read the message eagerly to avoid this situation.");
        }
    }

    /**
     * @return the {@literal "<lazy body reader>"} String.
     * To obtain the String representation of a HTTP message's body, first call
     * {@link #eager()}, then {@link EagerBodyReader#toString()}.
     */
    @Override
    public String toString() {
        return "<lazy body reader>";
    }
}
