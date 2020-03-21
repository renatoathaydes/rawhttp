package rawhttp.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A {@link RawHttpResponse}'s start-line.
 */
public class StatusLine implements StartLine {

    private final HttpVersion httpVersion;
    private final int statusCode;
    private final String reason;

    public StatusLine(HttpVersion httpVersion, int statusCode, String reason) {
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.reason = reason;
    }

    @Override
    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    /**
     * @return the status code in this status-code line.
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns true if this {@link StatusLine} indicates a HTTP redirection.
     *
     * @return true if the status code indicates a HTTP redirection
     * @see StatusLine#isRedirectCode(int)
     */
    public boolean isRedirect() {
        return isRedirectCode(statusCode);
    }

    /**
     * @return the reason phrase in this status-code line.
     */
    public String getReason() {
        return reason;
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        byte[] bytes = toString().getBytes(StandardCharsets.US_ASCII);
        outputStream.write(bytes);
        outputStream.write('\r');
        outputStream.write('\n');
    }

    /**
     * @return the start-line for this status-code line.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(httpVersion);
        builder.append(' ');
        builder.append(statusCode);
        if (!reason.isEmpty()) {
            builder.append(' ');
            builder.append(reason);
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusLine that = (StatusLine) o;
        return statusCode == that.statusCode &&
                httpVersion == that.httpVersion &&
                reason.equals(that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpVersion, statusCode, reason);
    }

    /**
     * Returns true if the given status code indicates a HTTP redirection.
     *
     * @param statusCode HTTP status code
     * @return true if this is a redirection status code, false otherwise
     */
    public static boolean isRedirectCode(int statusCode) {
        switch (statusCode) {
            case 300:
            case 301:
            case 302:
            case 303:
            case 307:
            case 308:
                return true;
            default:
                return false;
        }
    }

}
