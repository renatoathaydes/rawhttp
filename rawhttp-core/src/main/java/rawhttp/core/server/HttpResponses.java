package rawhttp.core.server;

import java.io.IOException;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.HttpMetadataParser;
import rawhttp.core.HttpVersion;
import rawhttp.core.StatusLine;

import static rawhttp.core.server.TcpRawHttpServer.STRICT_HTTP;

final class HttpResponses {

    private static final EagerHttpResponse<Void> NOT_FOUND_404_HTTP1_0;
    private static final EagerHttpResponse<Void> NOT_FOUND_404_HTTP1_1;
    private static final EagerHttpResponse<Void> SERVER_ERROR_500_HTTP1_0;
    private static final EagerHttpResponse<Void> SERVER_ERROR_500_HTTP1_1;

    private static final StatusLine STATUS_404_HTTP1_0;
    private static final StatusLine STATUS_404_HTTP1_1;
    private static final StatusLine STATUS_500_HTTP1_0;
    private static final StatusLine STATUS_500_HTTP1_1;

    static {
        HttpMetadataParser metadataParser = STRICT_HTTP.getMetadataParser();

        STATUS_404_HTTP1_0 = metadataParser.parseStatusLine(
                "HTTP/1.0 404 Not Found");
        STATUS_404_HTTP1_1 = metadataParser.parseStatusLine(
                "HTTP/1.1 404 Not Found");
        STATUS_500_HTTP1_0 = metadataParser.parseStatusLine(
                "HTTP/1.0 500 Server Error");
        STATUS_500_HTTP1_1 = metadataParser.parseStatusLine(
                "HTTP/1.1 500 Server Error");

        try {
            NOT_FOUND_404_HTTP1_1 = STRICT_HTTP.parseResponse(
                    STATUS_404_HTTP1_1 + "\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 23\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "Resource was not found.").eagerly();

            NOT_FOUND_404_HTTP1_0 = NOT_FOUND_404_HTTP1_1.withStatusLine(STATUS_404_HTTP1_0);

            SERVER_ERROR_500_HTTP1_1 = STRICT_HTTP.parseResponse(
                    STATUS_500_HTTP1_1 + "\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 28\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "\r\n" +
                            "A Server Error has occurred.").eagerly();

            SERVER_ERROR_500_HTTP1_0 = SERVER_ERROR_500_HTTP1_1.withStatusLine(STATUS_500_HTTP1_0);
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
