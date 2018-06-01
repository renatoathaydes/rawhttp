package rawhttp.core.body;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

/**
 * The consumer of a HTTP message body associated with a specific type of {@link FramedBody}.
 */
public abstract class BodyConsumer {

    public static final int DEFAULT_BUFFER_SIZE = 4096;

    private BodyConsumer() {
        // do not allow sub-types outside of this class
    }

    /**
     * Consume the HTTP message body fully, including any metadata used to frame the body.
     *
     * @param inputStream the raw input stream
     * @return the exact bytes of the message body
     * @throws IOException if an error occurs while consuming the message body
     */
    public byte[] consume(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        consumeInto(inputStream, out, DEFAULT_BUFFER_SIZE);
        return out.toByteArray();
    }

    /**
     * Consume the HTTP message body fully, excluding framing metadata.
     * <p>
     * For the {@link rawhttp.core.body.FramedBody.Chunked} frame, for example, this method returns only the actual
     * data which is wrapped into the chunks, but not the chunk-size, attributes and trailer-part.
     *
     * @param inputStream the raw input stream
     * @return the bytes of the data included in the message body
     * @throws IOException if an error occurs while consuming the message body
     */
    public byte[] consumeData(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        consumeDataInto(inputStream, out, DEFAULT_BUFFER_SIZE);
        return out.toByteArray();
    }

    /**
     * Consume the HTTP message body obtained by reading the input stream into the given output stream.
     *
     * @param inputStream  to read original message body from
     * @param outputStream stream to write the message body to
     * @param bufferSize   the size of the buffer to use, if possible. A non-positive value must be ignored.
     * @throws IOException if an error occurs while reading or writing the streams
     */
    public abstract void consumeInto(
            InputStream inputStream,
            OutputStream outputStream,
            int bufferSize) throws IOException;

    /**
     * Consume the HTTP message body fully, excluding framing metadata, into the given output stream.
     * <p>
     * For the {@link rawhttp.core.body.FramedBody.Chunked} frame, for example, this method consumes only the actual
     * data which is wrapped into the chunks, but not the chunk-size, attributes and trailer-part.
     *
     * @param inputStream  to read original message body from
     * @param outputStream stream to write the decoded message body to
     * @param bufferSize   the size of the buffer to use, if possible
     * @throws IOException if an error occurs while reading or writing the streams
     */
    public abstract void consumeDataInto(InputStream inputStream,
                                         OutputStream outputStream,
                                         int bufferSize) throws IOException;

    /**
     * Consumer of HTTP message body framed as {@link FramedBody.Chunked}.
     */
    public static final class ChunkedBodyConsumer extends BodyConsumer {

        private final ChunkedBodyParser bodyParser;

        ChunkedBodyConsumer(ChunkedBodyParser bodyParser) {
            this.bodyParser = bodyParser;
        }

        @Override
        public void consumeInto(
                InputStream inputStream,
                OutputStream outputStream,
                int bufferSize) throws IOException {
            bodyParser.parseChunkedBody(inputStream,
                    chunk -> chunk.writeTo(outputStream),
                    trailer -> trailer.writeTo(outputStream));
        }

        @Override
        public void consumeDataInto(InputStream inputStream, OutputStream out, int bufferSize)
                throws IOException {
            bodyParser.parseChunkedBody(inputStream,
                    chunk -> out.write(chunk.getData()),
                    trailer -> {
                        // ignore trailer
                    });
        }

        /**
         * Consume the given stream lazily, one chunk at a time.
         * <p>
         * The last chunk is always the empty chunk, so once the empty chunk is received,
         * trying to consume another chunk will result in an error.
         *
         * @param inputStream supplying a chunked body
         * @return lazy iterator of chunks
         */
        public Iterator<ChunkedBodyContents.Chunk> consumeLazily(InputStream inputStream) {
            return bodyParser.readLazily(inputStream);
        }

    }

    /**
     * Consumer of HTTP message body framed as {@link FramedBody.ContentLength}.
     */
    public static final class ContentLengthBodyConsumer extends BodyConsumer {

        private final long bodyLength;

        ContentLengthBodyConsumer(long bodyLength) {
            this.bodyLength = bodyLength;
        }

        @Override
        public void consumeInto(InputStream inputStream,
                                OutputStream outputStream,
                                int bufferSize) throws IOException {
            readAndWriteBytesUpToLength(inputStream, bodyLength, outputStream, bufferSize);
        }

        @Override
        public void consumeDataInto(InputStream inputStream, OutputStream outputStream, int bufferSize) throws IOException {
            consumeInto(inputStream, outputStream, bufferSize);
        }

        private static void readAndWriteBytesUpToLength(InputStream inputStream,
                                                        long bodyLength,
                                                        OutputStream outputStream,
                                                        int bufferSize) throws IOException {
            if (bufferSize <= 0) {
                bufferSize = DEFAULT_BUFFER_SIZE;
            }
            long offset = 0L;
            byte[] bytes = new byte[(int) Math.min(bodyLength, bufferSize)];
            while (offset < bodyLength) {
                int bytesToRead = (int) Math.min(bytes.length, bodyLength - offset);
                int actuallyRead = inputStream.read(bytes, 0, bytesToRead);
                if (actuallyRead < 0) {
                    throw new IOException("InputStream provided " + offset + ", but " + bodyLength + " were expected");
                } else {
                    outputStream.write(bytes, 0, actuallyRead);
                }
                offset += actuallyRead;
            }
        }

    }

    /**
     * Consumer of HTTP message body framed as {@link FramedBody.CloseTerminated}.
     */
    public static class CloseTerminatedBodyConsumer extends BodyConsumer {

        private static final CloseTerminatedBodyConsumer INSTANCE = new CloseTerminatedBodyConsumer();

        public static CloseTerminatedBodyConsumer getInstance() {
            return INSTANCE;
        }

        private CloseTerminatedBodyConsumer() {
            // hide
        }

        @Override
        public void consumeInto(InputStream inputStream,
                                OutputStream outputStream,
                                int bufferSize) throws IOException {
            if (bufferSize <= 0) {
                bufferSize = DEFAULT_BUFFER_SIZE;
            }
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        @Override
        public void consumeDataInto(InputStream inputStream, OutputStream outputStream, int bufferSize) throws IOException {
            consumeInto(inputStream, outputStream, bufferSize);
        }
    }

}
