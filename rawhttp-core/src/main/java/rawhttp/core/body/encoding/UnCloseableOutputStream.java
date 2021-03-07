package rawhttp.core.body.encoding;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class UnCloseableOutputStream extends FilterOutputStream {

    public UnCloseableOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
