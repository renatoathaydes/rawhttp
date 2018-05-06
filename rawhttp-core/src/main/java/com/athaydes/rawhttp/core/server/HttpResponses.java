package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.HttpVersion;
import com.athaydes.rawhttp.core.RawHttp;
import com.athaydes.rawhttp.core.StatusCodeLine;
import java.io.IOException;

import static com.athaydes.rawhttp.core.server.TcpRawHttpServer.STRICT_HTTP;

final class HttpResponses {

    private static final EagerHttpResponse<Void> NOT_FOUND_404_HTTP1_0;
    private static final EagerHttpResponse<Void> NOT_FOUND_404_HTTP1_1;
    private static final EagerHttpResponse<Void> SERVER_ERROR_500_HTTP1_0;
    private static final EagerHttpResponse<Void> SERVER_ERROR_500_HTTP1_1;

    private static final StatusCodeLine STATUS_404_HTTP1_0 = RawHttp.parseStatusCodeLine("HTTP/1.0 404 Not Found");
    private static final StatusCodeLine STATUS_404_HTTP1_1 = RawHttp.parseStatusCodeLine("HTTP/1.1 404 Not Found");
    private static final StatusCodeLine STATUS_500_HTTP1_0 = RawHttp.parseStatusCodeLine("HTTP/1.0 500 Server Error");
    private static final StatusCodeLine STATUS_500_HTTP1_1 = RawHttp.parseStatusCodeLine("HTTP/1.1 500 Server Error");

    static {
        try {
            NOT_FOUND_404_HTTP1_1 = STRICT_HTTP.parseResponse(
                    STATUS_404_HTTP1_1 + "\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 23\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "Resource was not found.").eagerly();

            NOT_FOUND_404_HTTP1_0 = NOT_FOUND_404_HTTP1_1.withStatusCodeLine(STATUS_404_HTTP1_0);

            SERVER_ERROR_500_HTTP1_1 = STRICT_HTTP.parseResponse(
                    STATUS_500_HTTP1_1 + "\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 28\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "A Server Error has occurred.").eagerly();

            SERVER_ERROR_500_HTTP1_0 = SERVER_ERROR_500_HTTP1_1.withStatusCodeLine(STATUS_500_HTTP1_0);
        } catch (IOException e) {
            throw new IllegalStateException("Default HTTP responses could not be parsed");
        }
    }

    static EagerHttpResponse<Void> getNotFoundResponse(HttpVersion httpVersion) {
        if (httpVersion.isOlderThan(HttpVersion.HTTP_1_1)) {
            return NOT_FOUND_404_HTTP1_0;
        } else {
            return NOT_FOUND_404_HTTP1_1;
        }
    }

    static EagerHttpResponse<Void> getServerErrorResponse(HttpVersion httpVersion) {
        if (httpVersion.isOlderThan(HttpVersion.HTTP_1_1)) {
            return SERVER_ERROR_500_HTTP1_0;
        } else {
            return SERVER_ERROR_500_HTTP1_1;
        }
    }

}
