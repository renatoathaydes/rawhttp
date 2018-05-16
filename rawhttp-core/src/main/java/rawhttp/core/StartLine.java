package rawhttp.core;

/**
 * HTTP message start line.
 * <p>
 * See <a href="https://tools.ietf.org/html/rfc7230#section-3.1">Section 3.1</a> of RFC-7230.
 */
public interface StartLine extends Writable {

    /**
     * @return message's HTTP version.
     */
    HttpVersion getHttpVersion();
}
