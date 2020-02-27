package com.athaydes.rawhttp.reqinedit;

import java.io.IOException;

public interface FileReader {
    byte[] read(String path) throws IOException;
}
