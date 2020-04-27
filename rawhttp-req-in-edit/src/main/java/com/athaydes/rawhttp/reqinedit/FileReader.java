package com.athaydes.rawhttp.reqinedit;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Simple file reader.
 */
@FunctionalInterface
public interface FileReader {
    /**
     * Read the full contents of the file at the given path as a byte array.
     *
     * @param path of file
     * @return bytes of the file
     * @throws IOException if a problem occurs reading the file
     */
    byte[] read(Path path) throws IOException;
}
