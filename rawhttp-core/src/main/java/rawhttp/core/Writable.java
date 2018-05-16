package rawhttp.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An entity that can be written out to an {@link java.io.OutputStream}.
 */
public interface Writable {

    /**
     * Write this to outputStream
     *
     * @param outputStream to write to
     * @throws IOException if an error occurs while writing to the given stream
     */
    void writeTo(OutputStream outputStream) throws IOException;
}
