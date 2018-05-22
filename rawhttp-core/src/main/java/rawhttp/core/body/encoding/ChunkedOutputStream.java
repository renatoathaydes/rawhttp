package rawhttp.core.body.encoding;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class ChunkedOutputStream extends FilterOutputStream {

    private static class ChunkSizeParser {
        char[] chars = new char[4];
        int index = 0;

        void write(char c) {
            if (index < 4) {
                chars[index++] = c;
            } else {
                throw new IllegalStateException("Invalid chunk-size (too big, more than 4 hex-digits)");
            }
        }

        int chunkSize() {
            if (index == 0) {
                throw new IllegalStateException("No chunk size available");
            }
            int result = Integer.parseInt(new String(chars, 0, index), 16);
            index = 0;
            return result;
        }

    }

    private enum ParsingState {
        CR_PRE_CHUNK_SIZE, LF_PRE_CHUNK_SIZE, CHUNK_SIZE, WRITING, DISCARDING_METADATA, DONE
    }

    private int bytesToWrite = 0;
    private ParsingState parsingState = ParsingState.CHUNK_SIZE;
    private final ChunkSizeParser sizeParser = new ChunkSizeParser();

    ChunkedOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        switch (parsingState) {
            case CR_PRE_CHUNK_SIZE:
                if (b != '\r') {
                    throw new IllegalStateException("Expected CR character, got byte " + b);
                }
                parsingState = ParsingState.LF_PRE_CHUNK_SIZE;
                break;
            case LF_PRE_CHUNK_SIZE:
                if (b != '\n') {
                    throw new IllegalStateException("Expected LF character, got byte " + b);
                }
                parsingState = ParsingState.CHUNK_SIZE;
                break;
            case CHUNK_SIZE:
                if (b == '\r' || b == ';') {
                    bytesToWrite = sizeParser.chunkSize();
                    if (bytesToWrite == 0) {
                        parsingState = ParsingState.DONE;
                    } else {
                        parsingState = ParsingState.DISCARDING_METADATA;
                    }
                } else {
                    sizeParser.write((char) b);
                }
                break;
            case DISCARDING_METADATA:
                if (b == '\n') {
                    parsingState = ParsingState.WRITING;
                }
                break;
            case WRITING:
                out.write(b);
                bytesToWrite--;
                if (bytesToWrite == 0) {
                    parsingState = ParsingState.CR_PRE_CHUNK_SIZE;
                }
                break;
            case DONE:
        }
    }

}
