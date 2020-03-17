package rawhttp.cookies;

import rawhttp.core.RawHttpHeaders;

import javax.annotation.Nullable;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility static methods to help HTTP servers set cookies.
 * <p>
 * Example usage:
 * <p>
 * <pre>
 * {@code
 * var headers = RawHttpHeaders.newBuilder();
 * var cookie = new HttpCookie("sid", "123456");
 * ServerCookieHelper.withCookie(headers, cookie);
 * var response = new RawHttp().parseResponse("200 OK")
 *   .withHeaders(headers.build());
 * }
 * </pre>
 */
public final class ServerCookieHelper {

    public static final String SET_COOKIE_HEADER = "Set-Cookie";

    private ServerCookieHelper() {
    }

    public static RawHttpHeaders withCookies(RawHttpHeaders headers, List<HttpCookie> cookies) {
        return withCookies(headers, cookies, null);
    }

    public static RawHttpHeaders withCookies(RawHttpHeaders headers, List<HttpCookie> cookies,
                                             @Nullable SameSite sameSite) {
        return withCookies(RawHttpHeaders.newBuilder(headers), cookies, sameSite).build();
    }

    public static RawHttpHeaders.Builder withCookies(RawHttpHeaders.Builder headers,
                                                     List<HttpCookie> cookies) {
        return withCookies(headers, cookies, null);
    }

    public static RawHttpHeaders.Builder withCookies(RawHttpHeaders.Builder headers,
                                                     List<HttpCookie> cookies,
                                                     @Nullable SameSite sameSite) {
        for (HttpCookie cookie : cookies) {
            headers.with(SET_COOKIE_HEADER, headerValue(cookie, sameSite));
        }
        return headers;
    }

    public static RawHttpHeaders withCookie(RawHttpHeaders headers, HttpCookie cookie) {
        return withCookie(headers, cookie, null);
    }

    public static RawHttpHeaders withCookie(RawHttpHeaders headers, HttpCookie cookie,
                                            @Nullable SameSite sameSite) {
        return withCookie(RawHttpHeaders.newBuilder(headers), cookie, sameSite).build();
    }

    public static RawHttpHeaders.Builder withCookie(RawHttpHeaders.Builder headers, HttpCookie cookie) {
        return withCookie(headers, cookie, null);
    }

    public static RawHttpHeaders.Builder withCookie(RawHttpHeaders.Builder headers, HttpCookie cookie,
                                                    @Nullable SameSite sameSite) {
        return headers.with(SET_COOKIE_HEADER, headerValue(cookie, sameSite));
    }

    /**
     * Compute the value of the "Set-Cookie" header to represent the given cookie,
     * with an optional {@link SameSite} attribute.
     *
     * @param cookie   the cookie
     * @param sameSite attribute for the cookie (given separately as {@link HttpCookie} does not currently
     *                 support it
     * @return the value of the "Set-Cookie" header
     */
    public static String headerValue(HttpCookie cookie, @Nullable SameSite sameSite) {
        StringBuilder builder = new StringBuilder(cookie.getName());
        builder.append("=\"").append(cookie.getValue()).append('"');
        List<String> attributes = attributesOf(cookie, sameSite);
        if (!attributes.isEmpty()) {
            builder.append(';');
            final int maxIndex = attributes.size() - 1;
            for (int i = 0; i < attributes.size(); i++) {
                builder.append(attributes.get(i));
                if (i != maxIndex) {
                    builder.append(';');
                }
            }
        }
        return builder.toString();
    }

    private static List<String> attributesOf(HttpCookie cookie, @Nullable SameSite sameSite) {
        List<String> result = new ArrayList<>(6);
        addAttributeIfNotEmpty(result, "Domain", cookie.getDomain());
        addAttributeIfNotEmpty(result, "Path", cookie.getPath());
        addAttributeIfNotEmpty(result, "Max-Age", cookie.getMaxAge() < 0 ? null : cookie.getMaxAge());
        addAttributeIfNotEmpty(result, "SameSite", sameSite);
        addAttributeIfTrue(result, "Secure", cookie.isHttpOnly());
        addAttributeIfTrue(result, "HttpOnly", cookie.isHttpOnly());
        return result;
    }

    private static void addAttributeIfNotEmpty(List<String> attributes, String attribute,
                                               @Nullable Object value) {
        if (value != null) {
            String textValue = value.toString();
            if (!textValue.isEmpty()) {
                attributes.add(attribute + "=" + textValue);
            }
        }
    }

    private static void addAttributeIfTrue(List<String> result, String attribute, boolean value) {
        if (value) {
            result.add(attribute);
        }
    }

}
