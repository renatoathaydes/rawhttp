package rawhttp.core.server;

import rawhttp.core.EagerHttpResponse;
import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.StatusLine;
import rawhttp.core.body.EagerBodyReader;

import static java.nio.charset.StandardCharsets.US_ASCII;

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
        STATUS_404_HTTP1_0 = new StatusLine(HttpVersion.HTTP_1_0, 404, "Not Found");
        STATUS_404_HTTP1_1 = new StatusLine(HttpVersion.HTTP_1_1, 404, "Not Found");

        STATUS_500_HTTP1_0 = new StatusLine(HttpVersion.HTTP_1_0, 500, "Server Error");
        STATUS_500_HTTP1_1 = new StatusLine(HttpVersion.HTTP_1_1, 500, "Server Error");

        final RawHttpHeaders basicHeaders = RawHttpHeaders.newBuilderSkippingValidation()
                .with("Content-Type", "text/plain")
                .with("Cache-Control", "no-cache")
                .with("Pragma", "no-cache")
                .build();

        byte[] notFoundResponseBody = "Resource was not found.".getBytes(US_ASCII);

        NOT_FOUND_404_HTTP1_1 = new EagerHttpResponse<>(null, null,
                STATUS_404_HTTP1_1,
                RawHttpHeaders.newBuilderSkippingValidation(basicHeaders)
                        .overwrite("Content-Length", Integer.toString(notFoundResponseBody.length))
                        .build(),
                new EagerBodyReader(notFoundResponseBody));

        NOT_FOUND_404_HTTP1_0 = NOT_FOUND_404_HTTP1_1.withStatusLine(STATUS_404_HTTP1_0);

        byte[] serverErrorResponseBody = "A Server Error has occurred.".getBytes(US_ASCII);

        SERVER_ERROR_500_HTTP1_1 = new EagerHttpResponse<>(null, null,
                STATUS_500_HTTP1_1,
                RawHttpHeaders.newBuilderSkippingValidation()
                        .overwrite("Content-Length", Integer.toString(serverErrorResponseBody.length))
                        .build(),
                new EagerBodyReader(serverErrorResponseBody));

        SERVER_ERROR_500_HTTP1_0 = SERVER_ERROR_500_HTTP1_1.withStatusLine(STATUS_500_HTTP1_0);

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
