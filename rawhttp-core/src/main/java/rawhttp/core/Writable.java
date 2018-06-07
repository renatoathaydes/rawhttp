package rawhttp.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An entity that can be written out to an {@link java.io.OutputStream}.
 */
public interface Writable {

    /**
     * Write this to the outputStream.
     * <p>
     * Line-endings marking the end of this entity must be written out by all implementations.
     *
     * @param outputStream to write to
     * @throws IOException if an error occurs while writing to the given stream
     */
    void writeTo(OutputStream outputStream) throws IOException;
}
