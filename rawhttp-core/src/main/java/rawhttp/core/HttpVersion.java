package rawhttp.core;

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
