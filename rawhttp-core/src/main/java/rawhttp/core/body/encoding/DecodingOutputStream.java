package rawhttp.core.body.encoding;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 */
public class DecodingOutputStream extends FilterOutputStream {

    public DecodingOutputStream(OutputStream out) {
        super(out);
    }

    public void finishDecoding() throws IOException {
        flush();
        if (out instanceof DecodingOutputStream) {
            ((DecodingOutputStream) out).finishDecoding();
        }
    }

    @Override
    public void close() throws IOException {
        // do not close the underlying OutputStream as it may be a client's stream
        finishDecoding();
    }

}
