package rawhttp.core.body;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The consumer of a HTTP message body associated with a specific type of {@link FramedBody}.
 */
public abstract class BodyConsumer {

    private BodyConsumer() {
        // do not allow sub-types outside of this class
    }

    /**
     * Consume the HTTP message body fully without decoding it.
     *
     * @param inputStream the raw input stream
     * @return the exact bytes of the message body, without any decoding being performe
     * @throws IOException if an error occurs while consuming the message body
     */
    public abstract byte[] consume(InputStream inputStream) throws IOException;

    /**
     * Consume the HTTP message body obtained by reading the input stream into the given output stream.
     *
     * @param inputStream  to read original message body from
     * @param outputStream stream to write the message body to
     * @param bufferSize   the size of the buffer to use, if possible
     * @throws IOException if an error occurs while reading or writing the streams
     */
    public abstract void consumeInto(
            InputStream inputStream,
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
        public byte[] consume(InputStream inputStream) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            consumeInto(inputStream, out, -1);
            return out.toByteArray();
        }

        @Override
        public void consumeInto(
                InputStream inputStream,
                OutputStream outputStream,
                int bufferSize) throws IOException {
            if (outputStream instanceof ChunkedOutputStream) {
                // we can decode the chunks directly, even though we can only start writing each
                // chunk after consuming it fully
                OutputStream decodeTarget = ((ChunkedOutputStream) outputStream).getDecodedChunksTarget();
                bodyParser.parseChunkedBody(inputStream,
                        chunk -> decodeTarget.write(chunk.getData()),
                        trailer -> { // ignore
                        });
            } else {
                bodyParser.parseChunkedBody(inputStream,
                        chunk -> chunk.writeTo(outputStream),
                        trailer -> trailer.writeTo(outputStream));
            }
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
        public byte[] consume(InputStream inputStream) throws IOException {
            int length = Math.toIntExact(bodyLength);
            return readBytesUpToLength(inputStream, length);
        }

        @Override
        public void consumeInto(InputStream inputStream,
                                OutputStream outputStream,
                                int bufferSize) throws IOException {
            readAndWriteBytesUpToLength(inputStream, bodyLength, outputStream, bufferSize);
        }

        private static byte[] readBytesUpToLength(InputStream inputStream,
                                                  int bodyLength) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bodyLength);
            readAndWriteBytesUpToLength(inputStream, bodyLength, outputStream, 4096);
            return outputStream.toByteArray();
        }

        private static void readAndWriteBytesUpToLength(InputStream inputStream,
                                                        long bodyLength,
                                                        OutputStream outputStream,
                                                        int bufferSize) throws IOException {
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
        public byte[] consume(InputStream inputStream) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }

        @Override
        public void consumeInto(InputStream inputStream,
                                OutputStream outputStream,
                                int bufferSize) throws IOException {
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

}
