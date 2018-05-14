package rawhttp.cli;

import java.io.IOException;
import rawhttp.core.EagerHttpResponse;
import rawhttp.core.HttpMetadataParser;
import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.StatusLine;
import rawhttp.core.body.StringBody;

import static rawhttp.core.server.TcpRawHttpServer.STRICT_HTTP;

final class HttpResponses {

    private static final StatusLine STATUS_200_HTTP1_0;
    private static final StatusLine STATUS_200_HTTP1_1;
    private static final StatusLine STATUS_405_HTTP1_0;
    private static final StatusLine STATUS_405_HTTP1_1;

    private static final RawHttpResponse<Void> OK_RESPONSE_HTTP1_0;
    private static final RawHttpResponse<Void> OK_RESPONSE_HTTP1_1;
    private static final EagerHttpResponse<Void> METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0;
    private static final EagerHttpResponse<Void> METHOD_NOT_ALLOWED_RESPONSE_HTTP1_1;

    static {
        HttpMetadataParser metadataParser = STRICT_HTTP.getMetadataParser();

        STATUS_200_HTTP1_0 = metadataParser.parseStatusLine(
                "HTTP/1.0 200 OK");
        STATUS_200_HTTP1_1 = metadataParser.parseStatusLine(
                "HTTP/1.1 200 OK");
        STATUS_405_HTTP1_0 = metadataParser.parseStatusLine(
                "HTTP/1.0 405 Method Not Allowed");
        STATUS_405_HTTP1_1 = metadataParser.parseStatusLine(
                "HTTP/1.1 405 Method Not Allowed");

        OK_RESPONSE_HTTP1_0 = new RawHttpResponse<>(null, null, STATUS_200_HTTP1_0,
                RawHttpHeaders.empty(), null);
        OK_RESPONSE_HTTP1_1 = OK_RESPONSE_HTTP1_0.withStatusLine(STATUS_200_HTTP1_1);

        StringBody status405body = new StringBody("Method not allowed.", "text/plain");

        try {
            METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0 = new RawHttpResponse<Void>(null, null,
                    STATUS_405_HTTP1_0, RawHttpHeaders.empty(), null)
                    .withBody(status405body)
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
