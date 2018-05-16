package rawhttp.core.body;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The consumer of a HTTP message body associated with a specific type of {@link BodyType}.
 */
public abstract class BodyConsumer {

    private BodyConsumer() {
        // do not allow sub-types outside of this class
    }

    /**
     * Get a {@link InputStream} that, when consumed, produces the decoded body of a HTTP message.
     *
     * @param inputStream the raw input stream
     * @return the decoded input stream
     */
    public abstract InputStream asDecodedStream(InputStream inputStream);

    /**
     * Decode the HTTP message body.
     *
     * @param inputStream the raw input stream
     * @return the decoded HTTP message body
     * @throws IOException if an error occurs while reading the stream
     */
    public abstract byte[] decode(InputStream inputStream) throws IOException;

    /**
     * Consume the HTTP message body fully without decoding it.
     *
     * @param inputStream the raw input stream
     * @return the exact bytes of the message body, without any decoding being performe
     * @throws IOException if an error occurs while consuming the message body
     */
    public abstract byte[] consume(InputStream inputStream) throws IOException;

    /**
     * Write the HTTP message body obtained by consuming the input stream to the given output stream.
     *
     * @param inputStream  to read original message body from
     * @param outputStream stream to write the message body to
     * @param bufferSize   the size of the buffer to use, if possible
     * @throws IOException if an error occurs while writing to the stream
     */
    public abstract void readAndWrite(
            InputStream inputStream,
            OutputStream outputStream,
            int bufferSize) throws IOException;


    /**
     * Consumer of HTTP message body of type {@link rawhttp.core.body.BodyType.Chunked}.
     */
    public static final class ChunkedBodyConsumer extends BodyConsumer {

        private final ChunkedBodyParser bodyParser;

        ChunkedBodyConsumer(ChunkedBodyParser bodyParser) {
            this.bodyParser = bodyParser;
        }

        @Override
        public byte[] decode(InputStream inputStream) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bodyParser.parseChunkedBody(inputStream, chunk -> {
                outputStream.write(chunk.getData());
            }, rawHttpHeaders -> {
            });
            return outputStream.toByteArray();
        }

        @Override
        public InputStream asDecodedStream(InputStream inputStream) {
            return new InputStreamChunkDecoder(bodyParser, inputStream);
        }

        @Override
        public byte[] consume(InputStream inputStream) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            readAndWrite(inputStream, out, -1);
            return out.toByteArray();
        }

        @Override
        public void readAndWrite(
                InputStream inputStream,
                OutputStream outputStream,
                int bufferSize) throws IOException {
            bodyParser.parseChunkedBody(inputStream,
                    chunk -> chunk.writeTo(outputStream),
                    trailer -> trailer.writeTo(outputStream));
        }
    }

    /**
     * Consumer of HTTP message body of type {@link rawhttp.core.body.BodyType.ContentLength}.
     */
    public static final class ContentLengthBodyConsumer extends BodyConsumer {

        private final long bodyLength;

        ContentLengthBodyConsumer(long bodyLength) {
            this.bodyLength = bodyLength;
        }

        @Override
        public InputStream asDecodedStream(InputStream inputStream) {
            return inputStream;
        }

        @Override
        public byte[] decode(InputStream inputStream) throws IOException {
            return consume(inputStream);
        }

        @Override
        public byte[] consume(InputStream inputStream) throws IOException {
            int length = Math.toIntExact(bodyLength);
            return readBytesUpToLength(inputStream, length);
        }

        @Override
        public void readAndWrite(InputStream inputStream,
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
     * Consumer of HTTP message body of type {@link rawhttp.core.body.BodyType.CloseTerminated}.
     */
    public static class CloseTerminatedBodyConsumer extends BodyConsumer {

        private static final CloseTerminatedBodyConsumer INSTANCE = new CloseTerminatedBodyConsumer();

        static CloseTerminatedBodyConsumer getInstance() {
            return INSTANCE;
        }

        private CloseTerminatedBodyConsumer() {
            // hide
        }

        @Override
        public InputStream asDecodedStream(InputStream inputStream) {
            return inputStream;
        }

        @Override
        public byte[] decode(InputStream inputStream) throws IOException {
            return consume(inputStream);
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
        public void readAndWrite(InputStream inputStream,
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
