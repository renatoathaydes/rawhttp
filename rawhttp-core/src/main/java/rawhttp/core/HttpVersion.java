package rawhttp.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Known HTTP Versions.
 * <p>
 * Notice that {@link HttpVersion#HTTP_2} is NOT supported fully.
 */
public enum HttpVersion {

    HTTP_0_9("HTTP/0.9"),
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2("HTTP/2");

    private static final byte[] HTTP_0_9_BYTES;
    private static final byte[] HTTP_1_0_BYTES;
    private static final byte[] HTTP_1_1_BYTES;
    private static final byte[] HTTP_2_BYTES;

    static {
        HTTP_0_9_BYTES = new byte[]{72, 84, 84, 80, 47, 48, 46, 57};
        HTTP_1_0_BYTES = new byte[]{72, 84, 84, 80, 47, 49, 46, 48};
        HTTP_1_1_BYTES = new byte[]{72, 84, 84, 80, 47, 49, 46, 49};
        HTTP_2_BYTES = new byte[]{72, 84, 84, 80, 47, 50};
    }

    private final String version;

    HttpVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return version;
    }

    /**
     * @param other http version
     * @return true if this version is older than the other, false otherwise.
     */
    public boolean isOlderThan(HttpVersion other) {
        return ordinal() < other.ordinal();
    }

    public void writeTo(OutputStream out) throws IOException {
        switch (this) {
            case HTTP_0_9:
                out.write(HTTP_0_9_BYTES);
                break;
            case HTTP_1_0:
                out.write(HTTP_1_0_BYTES);
                break;
            case HTTP_1_1:
                out.write(HTTP_1_1_BYTES);
                break;
            case HTTP_2:
                out.write(HTTP_2_BYTES);
                break;
        }
    }

    public static HttpVersion parse(String value) {
        switch (value) {
            case "HTTP/0.9":
                return HTTP_0_9;
            case "HTTP/1.0":
                return HTTP_1_0;
            case "HTTP/1.1":
                return HTTP_1_1;
            case "HTTP/2":
                return HTTP_2;
            default:
                throw new IllegalArgumentException("Unknown HTTP version");
        }
    }

}
