package rawhttp.core.body;

import rawhttp.core.Writable;
import rawhttp.core.body.encoding.DecodingOutputStream;
import rawhttp.core.body.encoding.HttpBodyEncodingRegistry;
import rawhttp.core.errors.UnknownEncodingException;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * HTTP message body reader.
 */
public abstract class BodyReader implements Writable, Closeable {

    private final FramedBody framedBody;

    public BodyReader(FramedBody framedBody) {
        this.framedBody = framedBody;
    }

    /**
     * @return the framed body of the HTTP message.
     */
    public FramedBody getFramedBody() {
        return framedBody;
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
    public abstract InputStream asRawStream();

    /**
     * @return the length of the raw message body if known without consuming it first.
     */
    public abstract OptionalLong getLengthIfKnown();

    /**
     * Read the raw HTTP message body, simultaneously writing it to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out to write the HTTP body to
     * @throws IOException if an error occurs while writing the message
     * @see BodyReader#writeDecodedTo(OutputStream)
     */
    @Override
    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, BodyConsumer.DEFAULT_BUFFER_SIZE);
    }

    /**
     * Read the raw HTTP message body, simultaneously writing it to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out        to write the HTTP body to
     * @param bufferSize size of the buffer to use for writing, if possible
     * @throws IOException if an error occurs while writing the message
     * @see BodyReader#writeDecodedTo(OutputStream, int)
     */
    public void writeTo(OutputStream out, int bufferSize) throws IOException {
        framedBody.getBodyConsumer().consumeInto(asRawStream(), out, bufferSize);
    }

    /**
     * Read the HTTP message body, simultaneously unframing and decoding it,
     * then writing the decoded body to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out to write the unframed, decoded message body to
     * @throws IOException if an error occurs while writing the message
     */
    public void writeDecodedTo(OutputStream out) throws IOException {
        writeDecodedTo(out, BodyConsumer.DEFAULT_BUFFER_SIZE);
    }

    /**
     * Read the HTTP message body, simultaneously unframing and decoding it,
     * then writing the decoded body to the given output.
     * <p>
     * This method may not validate the full HTTP message before it starts writing it out.
     * To perform a full validation first, call {@link #eager()} to get an eager reader.
     *
     * @param out        to write the unframed, decoded message body to
     * @param bufferSize size of the buffer to use for writing, if possible
     * @throws IOException              if an error occurs while writing the message
     * @throws UnknownEncodingException if the body is encoded with an encoding that is unknown
     *                                  by the {@link HttpBodyEncodingRegistry}.
     */
    public void writeDecodedTo(OutputStream out, int bufferSize) throws IOException {
        DecodingOutputStream decodedStream = framedBody.getBodyDecoder().decoding(out);
        try {
            framedBody.getBodyConsumer().consumeDataInto(asRawStream(), decodedStream, bufferSize);
            decodedStream.finishDecoding();
        } catch (InterruptedIOException e) {
            // this thread is interrupted when there's an Exception in the consumer Thread...
            // closing the stream should cause the original Exception to propagate to the caller
            decodedStream.close();

            // if close doesn't throw, we need to re-throw the error as things are not fine!
            throw e;
        }
    }

    /**
     * @return the raw HTTP message's body as bytes.
     * <p>
     * This method does not unframe nor decode the body in case the body is encoded.
     * To get the decoded body, use
     * {@link #decodeBody()} or {@link #decodeBodyToString(Charset)}.
     * @throws IOException if an error occurs while consuming the message body
     */
    public byte[] asRawBytes() throws IOException {
        return framedBody.getBodyConsumer().consume(asRawStream());
    }

    /**
     * @return true if the body is framed with the "chunked" encoding, false otherwise.
     */
    public boolean isChunked() {
        return framedBody instanceof FramedBody.Chunked;
    }

    /**
     * @return the body of the HTTP message as a {@link ChunkedBodyContents} if the body indeed used
     * the chunked transfer coding. If the body was not chunked, this method returns an empty value.
     * @throws IOException if an error occurs while consuming the message body
     */
    public Optional<ChunkedBodyContents> asChunkedBodyContents() throws IOException {
        return framedBody.use(
                cl -> Optional.empty(),
                chunked -> Optional.of(chunked.getContents(asRawStream())),
                ct -> Optional.empty());
    }

    /**
     * Get a lazy stream of chunks if the message body is chunked, or empty otherwise.
     * <p>
     * The last chunk is always the empty chunk, so once the empty chunk is received,
     * trying to consume another chunk will result in an error.
     *
     * @return lazy stream of chunks if the message body is chunked, or empty otherwise.
     * @throws IOException if an error occurs while consuming the body
     */
    public Optional<Iterator<ChunkedBodyContents.Chunk>> asChunkStream() throws IOException {
        BodyConsumer consumer = framedBody.getBodyConsumer();
        if (consumer instanceof BodyConsumer.ChunkedBodyConsumer) {
            try {
                return Optional.of(((BodyConsumer.ChunkedBodyConsumer) consumer)
                        .consumeLazily(asRawStream()));
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw e;
            }
        } else {
            return Optional.empty();
        }
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
    public String asRawString(Charset charset) throws IOException {
        return new String(asRawBytes(), charset);
    }

    /**
     * Unframe and decode the HTTP message's body.
     *
     * @return the unframed, decoded message body
     * @throws IOException if an error occurs while consuming the message body
     */
    public byte[] decodeBody() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeDecodedTo(out, BodyConsumer.DEFAULT_BUFFER_SIZE);
        return out.toByteArray();
    }

    /**
     * Unframe and decode the HTTP message's body, then turn it into a String using the given charset.
     *
     * @param charset to use to convert the body into a String
     * @return the decoded message body as a String
     * @throws IOException if an error occurs while consuming the message body
     */
    public String decodeBodyToString(Charset charset) throws IOException {
        return new String(decodeBody(), charset);
    }

}
