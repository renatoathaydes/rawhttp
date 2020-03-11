package com.athaydes.rawhttp.reqinedit;

import java.io.IOException;
import java.nio.file.Path;

public interface FileReader {
    byte[] read(Path path) throws IOException;
}
