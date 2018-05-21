package rawhttp.core.body;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import rawhttp.core.HttpMetadataParser;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpOptions;
import rawhttp.core.body.encoding.ChunkDecoder;
import rawhttp.core.body.encoding.HttpBodyEncodingRegistry;

/**
 * This class encodes the contents of a {@link InputStream} with the "chunked" Transfer-Encoding.
 * <p>
 * The {@link InputStream} contents are expected to NOT be encoded. To parse the contents of a stream which is
 * already "chunk" encoded, use {@link ChunkedBodyContents}.
 */
public class ChunkedBody extends HttpMessageBody {

    private final InputStream stream;
    private final int chunkLength;
    private final HttpMetadataParser metadataParser;

    private final HttpBodyEncodingRegistry chunkedRegistry;

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
        this(stream, contentType, chunkLength, new HttpMetadataParser(RawHttpOptions.defaultInstance()));
    }

    /**
     * Create a new {@link ChunkedBody} to encode the contents of the given stream.
     * <p>
     * The stream is read lazily, so it shouldn't be closed until this body is consumed.
     *
     * @param stream         content to encode
     * @param contentType    Content-Type of the stream contents
     * @param chunkLength    the length of each chunk
     * @param metadataParser metadata parser (chunked body may contain metadata)
     */
    public ChunkedBody(InputStream stream, @Nullable String contentType,
                       int chunkLength, HttpMetadataParser metadataParser) {
        super(contentType);
        this.stream = stream;
        this.chunkLength = chunkLength;
        this.metadataParser = metadataParser;
        this.chunkedRegistry = (enc) -> "chunked".equalsIgnoreCase(enc)
                ? Optional.of(new ChunkDecoder())
                : Optional.empty();
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
        List<String> encodings = Collections.singletonList("chunked");
        return new LazyBodyReader(new FramedBody.Chunked(new BodyDecoder(chunkedRegistry, encodings), metadataParser),
                new InputStreamChunkEncoder(stream, chunkLength));
    }

    @Override
    public RawHttpHeaders headersFrom(RawHttpHeaders headers) {
        RawHttpHeaders.Builder builder = RawHttpHeaders.newBuilder(headers);
        getContentType().ifPresent(contentType -> builder.overwrite("Content-Type", contentType));
        builder.overwrite("Transfer-Encoding", "chunked");
        builder.remove("Content-Length");
        return builder.build();
    }

}
