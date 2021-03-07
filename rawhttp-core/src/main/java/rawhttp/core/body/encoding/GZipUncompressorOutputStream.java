package rawhttp.core.body.encoding;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;
import java.util.zip.ZipException;

final class GZipUncompressorOutputStream extends InflaterOutputStream {

    /*
     * File header flags.
     */
    private final static int FTEXT      = 1;    // Extra text
    private final static int FHCRC      = 2;    // Header CRC
    private final static int FEXTRA     = 4;    // Extra field
    private final static int FNAME      = 8;    // File name
    private final static int FCOMMENT   = 16;   // File comment

    private static class ShortReader {
        private Integer first = null;
        private Integer second = null;

        boolean isReady() {
            return first != null && second != null;
        }

        boolean read(int b) {
            if (first == null) {
                first = b;
                return false;
            } else if (second == null) {
                second = b;
                return true;
            } else {
                throw new IllegalStateException("Cant read new bytes before resetting");
            }
        }

        int getAndReset() {
            if (!isReady()) {
                throw new IllegalStateException("Cant get a half reader");
            }

            try {
                return (second << 8) | first;
            } finally {
                first = null;
                second = null;
            }
        }
    }

    private enum HeaderReadingState {
        GZIP_MAGIC, COMPRESSION_LEVEL, READ_FLAG, SKIP_6_BYTES, PRE_SKIP_EXTRA_FIELDS, SKIP_EXTRA_FIELDS,
        SKIP_OPTIONAL_FILE_NAME, SKIP_OPTIONAL_COMMENT, CHECK_OPTIONAL_CRC_HEADER, DONE
    }

    private final CRC32 crc = new CRC32();
    private final ShortReader shortReader = new ShortReader();

    private HeaderReadingState state = HeaderReadingState.GZIP_MAGIC;
    private int flag = 0;
    private int toSkip = 0;

    GZipUncompressorOutputStream(OutputStream out, int bufferSize) {
        super(out, new Inflater(true), bufferSize);
    }

    private void readHeader(int b) throws IOException {
        b = asByte(b);
        switch (state) {
            case GZIP_MAGIC:
                if (shortReader.read(b)) {
                    b = shortReader.getAndReset();

                    if (b != GZIPInputStream.GZIP_MAGIC) {
                        throw new ZipException("Not in GZIP format");
                    }
                    resolveState();
                }
                break;

            case COMPRESSION_LEVEL:
                if (b != 8) {
                    throw new ZipException("Unsupported compression method");
                }
                resolveState();
                break;

            case READ_FLAG:
                resolveState();
                flag = b;
                toSkip = 5;
                break;

            case SKIP_6_BYTES:
            case SKIP_EXTRA_FIELDS:
                if (toSkip == 0) {
                    resolveState();
                } else toSkip -= 1;
                break;

            case PRE_SKIP_EXTRA_FIELDS:
                if (shortReader.read(b)) {
                    toSkip = shortReader.getAndReset();
                    resolveState();
                }
                break;

            case SKIP_OPTIONAL_FILE_NAME:
            case SKIP_OPTIONAL_COMMENT:
                if (b == 0) {
                    resolveState();
                }
                break;

            case CHECK_OPTIONAL_CRC_HEADER:
                int v = (int)crc.getValue() & 0xffff;
                if (b == v) {
                    throw new ZipException("Corrupt GZIP header");
                }
                resolveState();
                break;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException("Null buffer for read");
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (isReadingHeader()) {
            for (; len > 0 && isReadingHeader(); len--) {
                readHeader(b[off++]);
            }
            write(b, off, len);
        } else {
            super.write(b, off, len);
            crc.update(b, off, len);
        }
    }

    private boolean isReadingHeader() {
        return state != HeaderReadingState.DONE;
    }

    private void doneReadingHeader() {
        state = HeaderReadingState.DONE;
        crc.reset();
    }

    private int asByte(int b) {
        return b & 0xff;
    }

    private void resolveState() {
        if (isReadingHeader()) {
            switch (state) {
                case GZIP_MAGIC:
                    state = HeaderReadingState.COMPRESSION_LEVEL;
                    break;
                case COMPRESSION_LEVEL:
                    state = HeaderReadingState.READ_FLAG;
                    break;
                case READ_FLAG:
                    state = HeaderReadingState.SKIP_6_BYTES;
                    break;
                case PRE_SKIP_EXTRA_FIELDS:
                    state = HeaderReadingState.SKIP_EXTRA_FIELDS;
                    break;
                default:
                    int ordinal = state.ordinal();
                    if ((flag & FEXTRA) == FEXTRA && ordinal < HeaderReadingState.PRE_SKIP_EXTRA_FIELDS.ordinal()) {
                        state = HeaderReadingState.PRE_SKIP_EXTRA_FIELDS;
                    } else if ((flag & FNAME) == FNAME && ordinal < HeaderReadingState.SKIP_OPTIONAL_FILE_NAME.ordinal()) {
                        state = HeaderReadingState.SKIP_OPTIONAL_FILE_NAME;
                    } else if ((flag & FCOMMENT) == FCOMMENT && ordinal < HeaderReadingState.SKIP_OPTIONAL_COMMENT.ordinal()) {
                        state = HeaderReadingState.SKIP_OPTIONAL_COMMENT;
                    } else if ((flag & FHCRC) == FHCRC && ordinal < HeaderReadingState.CHECK_OPTIONAL_CRC_HEADER.ordinal()) {
                        state = HeaderReadingState.CHECK_OPTIONAL_CRC_HEADER;
                    } else {
                        doneReadingHeader();
                    }
                    break;
            }
        }
    }
}
