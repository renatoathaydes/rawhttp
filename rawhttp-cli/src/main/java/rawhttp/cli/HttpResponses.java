package rawhttp.cli;

import rawhttp.core.EagerHttpResponse;
import rawhttp.core.HttpVersion;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpResponse;
import rawhttp.core.StatusLine;
import rawhttp.core.body.EagerBodyReader;

import static java.nio.charset.StandardCharsets.US_ASCII;

final class HttpResponses {

    private static final StatusLine STATUS_200_HTTP1_0;
    private static final StatusLine STATUS_200_HTTP1_1;
    private static final StatusLine STATUS_405_HTTP1_0;
    private static final StatusLine STATUS_405_HTTP1_1;
    private static final StatusLine STATUS_304_HTTP1_0;
    private static final StatusLine STATUS_304_HTTP1_1;
    private static final StatusLine STATUS_412_HTTP1_0;
    private static final StatusLine STATUS_412_HTTP1_1;

    private static final RawHttpResponse<Void> OK_RESPONSE_HTTP1_0;
    private static final RawHttpResponse<Void> OK_RESPONSE_HTTP1_1;
    private static final EagerHttpResponse<Void> METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0;
    private static final EagerHttpResponse<Void> METHOD_NOT_ALLOWED_RESPONSE_HTTP1_1;
    private static final RawHttpResponse<Void> NOT_MODIFIED_RESPONSE_HTTP1_0;
    private static final RawHttpResponse<Void> NOT_MODIFIED_RESPONSE_HTTP1_1;
    private static final RawHttpResponse<Void> PRE_CONDITION_FAILED_RESPONSE_HTTP1_0;
    private static final RawHttpResponse<Void> PRE_CONDITION_FAILED_RESPONSE_HTTP1_1;

    static {
        STATUS_200_HTTP1_0 = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK");
        STATUS_200_HTTP1_1 = new StatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        STATUS_405_HTTP1_0 = new StatusLine(HttpVersion.HTTP_1_0, 405, "Method Not Allowed");
        STATUS_405_HTTP1_1 = new StatusLine(HttpVersion.HTTP_1_1, 405, "Method Not Allowed");
        STATUS_304_HTTP1_0 = new StatusLine(HttpVersion.HTTP_1_0, 304, "Not Modified");
        STATUS_304_HTTP1_1 = new StatusLine(HttpVersion.HTTP_1_1, 304, "Not Modified");
        STATUS_412_HTTP1_0 = new StatusLine(HttpVersion.HTTP_1_0, 412, "Precondition Failed");
        STATUS_412_HTTP1_1 = new StatusLine(HttpVersion.HTTP_1_1, 412, "Precondition Failed");

        final RawHttpHeaders basicHeaders = RawHttpHeaders.newBuilderSkippingValidation()
                .with("Content-Type", "text/plain")
                .build();

        OK_RESPONSE_HTTP1_0 = new EagerHttpResponse<>(null, null, STATUS_200_HTTP1_0, basicHeaders, null);
        OK_RESPONSE_HTTP1_1 = OK_RESPONSE_HTTP1_0.withStatusLine(STATUS_200_HTTP1_1);

        byte[] status405body = "Method not allowed.".getBytes(US_ASCII);

        METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0 = new EagerHttpResponse<>(null, null,
                STATUS_405_HTTP1_0, RawHttpHeaders.newBuilderSkippingValidation(basicHeaders)
                .overwrite("Content-Length", Integer.toString(status405body.length))
                .build(), new EagerBodyReader(status405body));

        METHOD_NOT_ALLOWED_RESPONSE_HTTP1_1 = METHOD_NOT_ALLOWED_RESPONSE_HTTP1_0
                .withStatusLine(STATUS_405_HTTP1_1);

        NOT_MODIFIED_RESPONSE_HTTP1_0 = new EagerHttpResponse<>(null, null,
                STATUS_304_HTTP1_0, RawHttpHeaders.empty(), null);

        NOT_MODIFIED_RESPONSE_HTTP1_1 = NOT_MODIFIED_RESPONSE_HTTP1_0
                .withStatusLine(STATUS_304_HTTP1_1);

        PRE_CONDITION_FAILED_RESPONSE_HTTP1_0 = new EagerHttpResponse<>(null, null,
                STATUS_412_HTTP1_0, RawHttpHeaders.empty(), null);

        PRE_CONDITION_FAILED_RESPONSE_HTTP1_1 = PRE_CONDITION_FAILED_RESPONSE_HTTP1_0
                .withStatusLine(STATUS_412_HTTP1_1);
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

    public static RawHttpResponse<Void> getNotModifiedResponse(HttpVersion httpVersion) {
        return httpVersion.isOlderThan(HttpVersion.HTTP_1_1)
                ? NOT_MODIFIED_RESPONSE_HTTP1_0
                : NOT_MODIFIED_RESPONSE_HTTP1_1;
    }

    public static RawHttpResponse<Void> getPreConditionFailedResponse(HttpVersion httpVersion) {
        return httpVersion.isOlderThan(HttpVersion.HTTP_1_1)
                ? PRE_CONDITION_FAILED_RESPONSE_HTTP1_0
                : PRE_CONDITION_FAILED_RESPONSE_HTTP1_1;
    }
}
