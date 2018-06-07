package com.athaydes.rawhttp.duplex.body;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.annotation.Nullable;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.body.ChunkedBody;
import rawhttp.core.body.ChunkedBodyContents;
import rawhttp.core.body.FramedBody;
import rawhttp.core.body.LazyBodyReader;

import static rawhttp.core.HttpMetadataParser.createStrictHttpMetadataParser;

/**
 * Extension of {@link ChunkedBody} that takes a potentially lazy iterator of
 * {@link rawhttp.core.body.ChunkedBodyContents.Chunk}s instead of an {@link InputStream}
 * as source of data.
 */
public class StreamedChunkedBody extends ChunkedBody {

    public StreamedChunkedBody(Iterator<ChunkedBodyContents.Chunk> chunkStream) {
        this(chunkStream, null);
    }

    public StreamedChunkedBody(Iterator<ChunkedBodyContents.Chunk> chunkStream,
                               @Nullable String contentType) {
        super(toInputStream(chunkStream), contentType, -1);
    }

    @Override
    public LazyBodyReader toBodyReader() {
        return new LazyBodyReader(
                new FramedBody.Chunked(getBodyDecoder(), createStrictHttpMetadataParser()),
                stream);
    }

    private static InputStream toInputStream(Iterator<ChunkedBodyContents.Chunk> chunkStream) {
        return new InputStream() {

            int byteIndex = 0;
            byte[] chunkBytes = new byte[0];
            boolean hasNextChunk = true;

            @Override
            public int read() throws IOException {
                if (byteIndex >= chunkBytes.length) {
                    if (hasNextChunk) {
                        readNextChunk();
                        byteIndex = 0;
                    } else {
                        return -1;
                    }
                }
                return chunkBytes[byteIndex++];
            }

            void readNextChunk() throws IOException {
                ChunkedBodyContents.Chunk chunk;
                if (chunkStream.hasNext()) {
                    chunk = chunkStream.next();
                } else {
                    chunk = new ChunkedBodyContents.Chunk(RawHttpHeaders.empty(), new byte[0]);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream(chunk.size() + 4);
                chunk.writeTo(out);
                chunkBytes = out.toByteArray();
                hasNextChunk = chunk.size() > 0;
            }
        };
    }

}
