package com.athaydes.rawhttp.core.server;

import com.athaydes.rawhttp.core.EagerHttpResponse;
import com.athaydes.rawhttp.core.RawHttp;
import java.io.IOException;

final class HttpResponses {

    static final EagerHttpResponse<Void> NOT_FOUND_404;
    static final EagerHttpResponse<Void> SERVER_ERROR_500;

    static {
        try {
            NOT_FOUND_404 = new RawHttp().parseResponse(
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 23\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "Resource was not found.").eagerly();

            SERVER_ERROR_500 = new RawHttp().parseResponse(
                    "HTTP/1.1 500 Server Error\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 28\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "A Server Error has occurred.").eagerly();
        } catch (IOException e) {
            throw new IllegalStateException("Default HTTP responses could not be parsed");
        }
    }

}
