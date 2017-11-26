package com.athaydes.rawhttp.core;

import java.io.IOException;

public interface RawHttpClient<Response> {

    RawHttpResponse<Response> send(RawHttpRequest request) throws IOException;

}
