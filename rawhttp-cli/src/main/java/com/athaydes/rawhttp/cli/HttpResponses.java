package com.athaydes.rawhttp.cli;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.HttpVersion;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.RawHttpResponse;
import com.athaydes.rawhttp.core.StatusLine;
import com.athaydes.rawhttp.core.body.StringBody;
import java.io.IOException;

import static com.athaydes.rawhttp.core.server.TcpRawHttpServer.STRICT_HTTP;

final class HttpResponses {

    private static final StatusLine STATUS_200_HTTP1_0 = RawHttp.parseStatusLine(
            "HTTP/1.0 200 OK", false);
    private static final StatusLine STATUS_200_HTTP1_1 = RawHttp.parseStatusLine(
            "HTTP/1.1 200 OK", false);
    private static final StatusLine STATUS_405_HTTP1_0 = RawHttp.parseStatusLine(
            "HTTP/1.0 405 Method Not Allowed", false);
    private static final StatusLine STATUS_405_HTTP1_1 = RawHttp.parseStatusLine("" +
            "HTTP/1.1 405 Method Not Allowed", false);

    private static final RawHttpResponse<Void> OK_RESPONSE_HTTP1_0;
    private static final RawHttpResponse<Void> OK_RESPONSE_HTTP1_1;
    private static final EagerHttpResponse<Void> METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0;
    private static final EagerHttpResponse<Void> METHOD_NOT_ALLOWED_RESPONSE_HTTP1_1;

    static {
        OK_RESPONSE_HTTP1_0 = STRICT_HTTP.parseResponse(STATUS_200_HTTP1_0 + "");
        OK_RESPONSE_HTTP1_1 = OK_RESPONSE_HTTP1_0.withStatusLine(STATUS_200_HTTP1_1);

        StringBody status405body = new StringBody("Method not allowed.", "text/plain");

        try {
            METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0 = STRICT_HTTP
                    .parseResponse(STATUS_405_HTTP1_0 + "")
                    .replaceBody(status405body)
                    .eagerly();

            METHOD_NOT_ALLOWED_RESPONSE_HTTP1_1 = METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0
                    .withStatusLine(STATUS_405_HTTP1_1);
        } catch (IOException e) {
            throw new IllegalStateException("Default responses could not be parsed");
        }
    }

    static RawHttpResponse<Void> getOkResponse(HttpVersion httpVersion) {
        if (httpVersion.isOlderThan(HttpVersion.HTTP_1_1)) {
            return OK_RESPONSE_HTTP1_0;
        } else {
            return OK_RESPONSE_HTTP1_1;
        }
    }

    static EagerHttpResponse<Void> getMethodNotAllowedResponse(HttpVersion httpVersion) {
        if (httpVersion.isOlderThan(HttpVersion.HTTP_1_1)) {
            return METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0;
        } else {
            return METHOD_NOT_ALLOWED_RESPONSE_HTTP1_1;
        }
    }

}
