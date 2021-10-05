package rawhttp.core.body;

import org.jetbrains.annotations.Nullable;
import rawhttp.core.HttpMetadataParser;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.body.encoding.ChunkDecoder;
import rawhttp.core.body.encoding.HttpBodyEncodingRegistry;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * This class encodes the contents of a {@link InputStream} with the "chunked" Transfer-Encoding.
 * <p>
 * The {@link InputStream} contents are expected to NOT be encoded. To parse the contents of a stream which is
 * already "chunk" encoded, use {@link ChunkedBodyContents}.
 */
public class ChunkedBody extends HttpMessageBody {

    protected final InputStream stream;
    private final int chunkLength;
    private final HttpMetadataParser metadataParser;

    /**
     * Create a new {@link ChunkedBody} to encode the contents of the given stream.
     * <p>
     * The stream is read lazily, so it shouldn't be closed until this body is consumed.
     * <p>
     * A default chunk size of 4096 bytes is used.
     *
     * @param stream content to encode
     */
    public ChunkedBody(InputStream stream) {
        this(stream, null, 4096, defaultChunkedBodyDecoder(),
                new HttpMetadataParser(RawHttpOptions.defaultInstance()));
    }

    /**
     * Create a new {@link ChunkedBody} to encode the contents of the given stream.
     * <p>
     * The stream is read lazily, so it shouldn't be closed until this body is consumed.
     *
     * @param stream      content to encode
     * @param contentType Content-Type of the stream contents
     * @param chunkLength the length of each chunk
     */
    public ChunkedBody(InputStream stream, @Nullable String contentType, int chunkLength) {
        this(stream, contentType, chunkLength, defaultChunkedBodyDecoder(),
                new HttpMetadataParser(RawHttpOptions.defaultInstance()));
    }

    /**
     * Create a new {@link ChunkedBody} to encode the contents of the given stream.
     * <p>
     * The stream is read lazily, so it shouldn't be closed until this body is consumed.
     *
     * @param stream         content to encode
     * @param contentType    Content-Type of the stream contents
     * @param chunkLength    the length of each chunk
     * @param bodyDecoder    decoder capable of decoding the body. The last encoding must be "chunked".
     * @param metadataParser metadata parser (chunked body may contain metadata)
     * @throws IllegalArgumentException if the bodyDecoder's last encoding is not "chunked"
     */
    public ChunkedBody(InputStream stream,
                       @Nullable String contentType,
                       int chunkLength,
                       BodyDecoder bodyDecoder,
                       HttpMetadataParser metadataParser) {
        super(contentType, bodyDecoder);
        this.stream = stream;
        this.chunkLength = chunkLength;
        this.metadataParser = metadataParser;
        validateEncodings(bodyDecoder.getEncodings());
    }

    private static void validateEncodings(List<String> encodings) {
        if (encodings.isEmpty() || !"chunked".equalsIgnoreCase(encodings.get(encodings.size() - 1))) {
            throw new IllegalArgumentException("Last encoding in BodyEncoder's encodings is not 'chunked': " +
                    encodings);
        }
    }

    /**
     * @return empty. "chunked"-encoded bodies are normally used for content for which the length is unknown.
     */
    @Override
    protected OptionalLong getContentLength() {
        return OptionalLong.empty();
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(new FramedBody.Chunked(getBodyDecoder(), metadataParser),
                new InputStreamChunkEncoder(stream, chunkLength));
    }

    @Override
    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilder(super.headersFrom(headers));
        builder.remove("Content-Length");
        return builder.build();
    }

    private static BodyDecoder defaultChunkedBodyDecoder() {
        HttpBodyEncodingRegistry registry = (enc) -> "chunked".equalsIgnoreCase(enc)
                ? Optional.of(new ChunkDecoder())
                : Optional.empty();
        List<String> encodings = Collections.singletonList("chunked");
        return new BodyDecoder(registry, encodings);
    }

}
