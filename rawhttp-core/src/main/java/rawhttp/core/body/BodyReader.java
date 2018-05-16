package rawhttp.core.body;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.OptionalLong;
import rawhttp.core.Writable;

/**
 * HTTP message body reader.
 */
public abstract class BodyReader implements Writable, Closeable {

    private final BodyType bodyType;

    public BodyReader(BodyType bodyType) {
        this.bodyType = bodyType;
    }

    /**
     * @return the type of the body of a HTTP message.
     */
    public BodyType getBodyType() {
        return bodyType;
    }

    /**
     * @return resolve the body of the HTTP message eagerly.
     * In effect, this method causes the body of the HTTP message to be consumed.
     * @throws IOException if an error occurs while reading the body
     */
    public abstract EagerBodyReader eager() throws IOException;

    /**
     * @return the stream which may produce the raw HTTP message body.
     * <p>
     * This method does not decode the body in case the body is encoded.
     * <p>
     * Notice that the stream may be closed if this {@link BodyReader} is closed.
     */
    public abstract InputStream asStream();

    /**
     * @return the length of this body if known without consuming it first.
     */
    public abstract OptionalLong getLengthIfKnown();

    /**
     * Read the HTTP message body, simultaneously writing it to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out to write the HTTP body to
     * @throws IOException if an error occurs while writing the message
     */
    @Override
    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, 4096);
    }

    /**
     * Read the HTTP message body, simultaneously writing it to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out        to write the HTTP body to
     * @param bufferSize size of the buffer to use for writing, if possible
     * @throws IOException if an error occurs while writing the message
     */
    public void writeTo(OutputStream out, int bufferSize) throws IOException {
        getBodyType().getBodyConsumer().readAndWrite(asStream(), out, bufferSize);
    }

    /**
     * Read the HTTP message body, simultaneously decoding it, then writing the decoded body to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out to write the decoded message body to
     * @throws IOException if an error occurs while writing the message
     */
    public void writeDecodedTo(OutputStream out) throws IOException {
        writeDecodedTo(out, 4096);
    }

    /**
     * Read the HTTP message body, simultaneously decoding it, then writing the decoded body to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out        to write the decoded message body to
     * @param bufferSize size of the buffer to use for writing, if possible
     * @throws IOException if an error occurs while writing the message
     */
    public void writeDecodedTo(OutputStream out, int bufferSize) throws IOException {
        InputStream decodedStream = getBodyType().getBodyConsumer().asDecodedStream(asStream());
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        while ((bytesRead = decodedStream.read(buffer)) > 0) {
            out.write(buffer, 0, bytesRead);
        }
    }

    /**
     * @return the HTTP message's body as bytes.
     * This method does not decode the body in case the body is encoded.
     * To get the decoded body, use
     * {@link #decodeBody()} or {@link #decodeBodyToString(Charset)}.
     * @throws IOException if an error occurs while consuming the message body
     */
    public byte[] asBytes() throws IOException {
        return bodyType.getBodyConsumer().consume(asStream());
    }

    /**
     * @return true if the body is encoded and framed with the "chunked" encoding, false otherwise.
     */
    public boolean isChunked() {
        return bodyType instanceof BodyType.Chunked;
    }

    /**
     * @return the body of the HTTP message as a {@link ChunkedBodyContents} if the body indeed used
     * the chunked transfer coding. If the body was not chunked, this method returns an empty value.
     * @throws IOException if an error occurs while consuming the message body
     */
    public Optional<ChunkedBodyContents> asChunkedBodyContents() throws IOException {
        return bodyType.use(
                cl -> Optional.empty(),
                chunked -> Optional.of(chunked.getContents(asStream())),
                ct -> Optional.empty());
    }

    /**
     * Convert the HTTP message's body into a String.
     * <p>
     * The body is returned without being decoded (i.e. raw).
     * To get the decoded body, use
     * {@link #decodeBody()} or {@link #decodeBodyToString(Charset)}.
     *
     * @param charset text message's charset
     * @return String representing the raw HTTP message's body.
     * @throws IOException if an error occurs while consuming the message body
     */
    public String asString(Charset charset) throws IOException {
        return new String(asBytes(), charset);
    }

    /**
     * Decode the HTTP message's body.
     *
     * @return the decoded message body
     * @throws IOException if an error occurs while consuming the message body
     */
    public byte[] decodeBody() throws IOException {
        return getBodyType().getBodyConsumer().decode(asStream());
    }

    /**
     * Decode the HTTP message's body, then turn it into a String using the given encoding.
     *
     * @param charset to use to convert the body into a String
     * @return the decoded message body as a String
     * @throws IOException if an error occurs while consuming the message body
     */
    public String decodeBodyToString(Charset charset) throws IOException {
        return new String(decodeBody(), charset);
    }

}
