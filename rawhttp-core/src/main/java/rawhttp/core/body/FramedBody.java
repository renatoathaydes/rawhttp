package rawhttp.core.body;

import rawhttp.core.HttpMetadataParser;
import rawhttp.core.IOFunction;
import rawhttp.core.RawHttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A framed HTTP message body.
 * <p>
 * This is a closed type with only 3 possible implementations:
 * <ul>
 * <li>{@link ContentLength}</li>
 * <li>{@link Chunked}</li>
 * <li>{@link CloseTerminated}</li>
 * </ul>
 */
public abstract class FramedBody {

    private final BodyDecoder bodyDecoder;

    // hidden, so only sub-types declared within this class can exist
    private FramedBody(BodyDecoder bodyDecoder) {
        this.bodyDecoder = bodyDecoder;
    }

    /**
     * @return the transfer encodings applied to the HTTP message body.
     * <p>
     * RFC-7230 explicitly mentions "chunked", "compress", "deflate" and "gzip", but others may also be applied
     * as long as they are included in the
     * <a href="https://tools.ietf.org/html/rfc7230#section-8.4">Transfer Coding Registry</a>.
     * <p>
     * RawHTTP can support custom encodings by being given custom implementations of
     * {@link rawhttp.core.body.encoding.HttpBodyEncodingRegistry} or, more simply, by using the
     * {@link rawhttp.core.body.encoding.ServiceLoaderHttpBodyEncodingRegistry} mechanism, which is the default.
     */
    public List<String> getEncodings() {
        return bodyDecoder.getEncodings();
    }

    /**
     * @return the body-encoding container which can be used to access the decoders required to decode the body
     * of a HTTP message.
     */
    public BodyDecoder getBodyDecoder() {
        return bodyDecoder;
    }

    /**
     * @return the consumer for this {@link FramedBody}
     */
    protected abstract BodyConsumer getBodyConsumer();

    /**
     * Use the un-framed message body, mapping each possible implementation into a value of type @{link T}.
     *
     * @param useContentLength   called if the body frame is {@link ContentLength}
     * @param useChunked         called if the body frame is {@link Chunked}
     * @param useCloseTerminated called if the body frame is {@link CloseTerminated}
     * @param <T>                type of returned Object
     * @return the value returned by the selected mapping function
     * @throws IOException if the selected function throws
     */
    public final <T> T use(IOFunction<ContentLength, T> useContentLength,
                           IOFunction<Chunked, T> useChunked,
                           IOFunction<CloseTerminated, T> useCloseTerminated) throws IOException {
        if (this instanceof ContentLength) {
            return useContentLength.apply((ContentLength) this);
        }
        if (this instanceof Chunked) {
            return useChunked.apply((Chunked) this);
        }
        if (this instanceof CloseTerminated) {
            return useCloseTerminated.apply((CloseTerminated) this);
        }
        throw new IllegalStateException("Unknown body type: " + this);
    }

    /**
     * Type of HTTP message body framed via the Content-Length header.
     */
    public static final class ContentLength extends FramedBody {

        private final long bodyLength;
        private final boolean allowContentLengthMismatch;

        /**
         * Create a new instance of the {@link ContentLength} framed body.
         *
         * @param bodyLength the length of the HTTP message body
         */
        public ContentLength(long bodyLength) {
            this(new BodyDecoder(), bodyLength);
        }

        /**
         * Create a new instance of the {@link ContentLength} framed body.
         *
         * @param bodyLength                 the length of the HTTP message body
         * @param allowContentLengthMismatch allow the content-length header to not match exactly a HTTP
         *                                   message's body length.
         */
        public ContentLength(long bodyLength, boolean allowContentLengthMismatch) {
            this(new BodyDecoder(), bodyLength, allowContentLengthMismatch);
        }

        /**
         * Create a new instance of the {@link ContentLength} framed body.
         *
         * @param bodyDecoder the body encoding
         * @param bodyLength  the length of the HTTP message body
         */
        public ContentLength(BodyDecoder bodyDecoder, long bodyLength) {
            this(bodyDecoder, bodyLength, false);
        }

        /**
         * Create a new instance of the {@link ContentLength} framed body.
         *
         * @param bodyDecoder                the body encoding
         * @param bodyLength                 the length of the HTTP message body
         * @param allowContentLengthMismatch allow the content-length header to not match exactly a HTTP
         *                                   message's body length.
         */
        public ContentLength(BodyDecoder bodyDecoder, long bodyLength, boolean allowContentLengthMismatch) {
            super(bodyDecoder);
            this.bodyLength = bodyLength;
            this.allowContentLengthMismatch = allowContentLengthMismatch;
        }

        public long getBodyLength() {
            return bodyLength;
        }

        public boolean isAllowContentLengthMismatch() {
            return allowContentLengthMismatch;
        }

        @Override
        protected BodyConsumer getBodyConsumer() {
            return new BodyConsumer.ContentLengthBodyConsumer(bodyLength, allowContentLengthMismatch);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ContentLength that = (ContentLength) other;
            return bodyLength == that.bodyLength;
        }

        @Override
        public int hashCode() {
            return (int) (bodyLength ^ (bodyLength >>> 32));
        }

        @Override
        public String toString() {
            return "ContentLength{" +
                    "value=" + bodyLength +
                    ", encodings=" + getEncodings() +
                    ", allowContentLengthMismatch=" + allowContentLengthMismatch +
                    '}';
        }
    }

    /**
     * The type of a HTTP message body whose last encoding is "chunked".
     * <p>
     * Notice that the "chunked" encoding serves as both a normal encoding and as a message framing strategy.
     */
    public static final class Chunked extends FramedBody {

        private final ChunkedBodyParser bodyParser;

        /**
         * Create a new instance of the {@link Chunked} body type.
         *
         * @param bodyDecoder    the body encoding
         * @param metadataParser parser for the body trailer part
         */
        public Chunked(BodyDecoder bodyDecoder, HttpMetadataParser metadataParser) {
            super(bodyDecoder);
            this.bodyParser = new ChunkedBodyParser(metadataParser);
        }

        public ChunkedBodyContents getContents(InputStream inputStream) throws IOException {
            List<ChunkedBodyContents.Chunk> chunks = new ArrayList<>();
            AtomicReference<RawHttpHeaders> headersRef = new AtomicReference<>();
            bodyParser.parseChunkedBody(inputStream, chunks::add, headersRef::set);
            return new ChunkedBodyContents(chunks, headersRef.get());
        }

        @Override
        public BodyConsumer getBodyConsumer() {
            return new BodyConsumer.ChunkedBodyConsumer(bodyParser);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            Chunked chunked = (Chunked) other;
            return getEncodings().equals(chunked.getEncodings());
        }

        @Override
        public int hashCode() {
            return getEncodings().hashCode();
        }

        @Override
        public String toString() {
            return "Chunked{" +
                    "encodings=" + getEncodings() +
                    '}';
        }
    }

    /**
     * The type of a HTTP message body whose body has no delimiters.
     * <p>
     * The stream providing the message body is simply read until the connection is closed.
     */
    public static final class CloseTerminated extends FramedBody {

        /**
         * Create a new instance of the {@link CloseTerminated} body type.
         *
         * @param bodyDecoder the body encoding
         */
        public CloseTerminated(BodyDecoder bodyDecoder) {
            super(bodyDecoder);
        }

        @Override
        protected BodyConsumer getBodyConsumer() {
            return BodyConsumer.CloseTerminatedBodyConsumer.getInstance();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            CloseTerminated chunked = (CloseTerminated) other;
            return getEncodings().equals(chunked.getEncodings());
        }

        @Override
        public int hashCode() {
            return getEncodings().hashCode();
        }

        @Override
        public String toString() {
            return "CloseTerminated{" +
                    "encodings=" + getEncodings() +
                    '}';
        }
    }
}
