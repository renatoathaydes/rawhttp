package com.athaydes.rawhttp.reqinedit;

import rawhttp.core.RawHttpResponse;

import java.io.IOException;

/**
 * HTTP response storage.
 */
public interface ResponseStorage {

    /**
     * Store the HTTP response at the location given by responseRef, as declared in the HTTP file.
     *
     * @param response    to store
     * @param responseRef response-ref declared in the HTTP file
     * @throws IOException if a problem occurs writing IO
     */
    void store(RawHttpResponse<?> response, String responseRef) throws IOException;
}
