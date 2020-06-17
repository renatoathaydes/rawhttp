package rawhttp.core;

import java.io.ByteArrayOutputStream;
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
        writeTo(outputStream, true);
    }

    /**
     * @return the start-line for this status-code line.
     */
    @Override
    public String toString() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        try {
            writeTo(out, false);
        } catch (IOException e) {
            // cannot happen, in-memory OutputStream used
        }
        return new String(out.toByteArray(), StandardCharsets.US_ASCII);
    }

    private void writeTo(OutputStream outputStream, boolean newLine) throws IOException {
        httpVersion.writeTo(outputStream);
        outputStream.write(' ');
        outputStream.write(Integer.toString(statusCode).getBytes(StandardCharsets.US_ASCII));
        if (!reason.isEmpty()) {
            outputStream.write(' ');
            // interpret using UTF-8 as the spec allows basically any bytes in it, so this seems to be our best chance!
            // reason-phrase = *( HTAB / SP / VCHAR / obs-text )
            outputStream.write(reason.getBytes(StandardCharsets.UTF_8));
        }

        if (newLine) {
            outputStream.write('\r');
            outputStream.write('\n');
        }
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
