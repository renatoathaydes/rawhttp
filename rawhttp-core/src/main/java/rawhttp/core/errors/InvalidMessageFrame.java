package rawhttp.core.errors;

/**
 * An Exception that occurs when the body of a HTTP message is incorrectly framed.
 * <p>
 * The rules for determining the frame of a HTTP message are enumerated in section
 * <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">3.3.3 of RFC-7230</a>.
 */
public class InvalidMessageFrame extends RuntimeException {

    public InvalidMessageFrame(String message) {
        super(message);
    }

}
