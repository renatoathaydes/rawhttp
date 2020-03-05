package com.athaydes.rawhttp.reqinedit;

import rawhttp.core.RawHttpResponse;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A simple file-based {@link ResponseStorage}.
 */
public final class FileResponseStorage implements ResponseStorage {

    @Override
    public void store(RawHttpResponse<?> response, String responseRef) throws IOException {
        try (FileOutputStream out = new FileOutputStream(responseRef)) {
            response.writeTo(out);
        }
    }
}
