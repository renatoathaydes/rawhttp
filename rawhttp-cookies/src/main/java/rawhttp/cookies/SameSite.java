package rawhttp.cookies;

/**
 * Same-Site Policy for cookies in browsers.
 * <p>
 * See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies">MDN SameSite Cookies</a>
 */
public enum SameSite {

    /**
     * The browser will send cookies with both cross-site requests and same-site requests.
     */
    NONE,

    /**
     * The browser will only send cookies for same-site requests
     * (requests originating from the site that set the cookie).
     * <p>
     * If the request originated from a different URL than the URL of the current location, none of the cookies
     * tagged with the Strict attribute will be included.
     */
    STRICT,

    /**
     * Same-site cookies are withheld on cross-site subrequests, such as calls to load images or frames,
     * but will be sent when a user navigates to the URL from an external site; for example, by following a
     * link.
     */
    LAX;

    @Override
    public String toString() {
        switch (this) {
            case NONE:
                return "None";
            case STRICT:
                return "Strict";
            case LAX:
                return "Lax";
            default:
                throw new IllegalStateException("Unknown value: " + name());
        }
    }

    /**
     * Parse the value of the SameSite cookie attribute.
     *
     * @param value of the SameSite attribute
     * @return the parsed value
     * @throws IllegalArgumentException if the value is not valid
     */
    public static SameSite parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
