package rawhttp.cookies;

import org.jetbrains.annotations.Nullable;
import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility static methods to help HTTP servers set cookies.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * var headers = RawHttpHeaders.newBuilder();
 * var cookie = new HttpCookie("sid", "123456");
 * ServerCookieHelper.setCookie(headers, cookie);
 * var response = new RawHttp().parseResponse("200 OK")
 *   .withHeaders(headers.build());
 * }
 * </pre>
 */
public final class ServerCookieHelper {

    /**
     * The name of the Cookie header sent by HTTP clients.
     */
    public static final String COOKIE_HEADER = "Cookie";

    /**
     * The name of the Set-Cookie header used by HTTP servers to set cookies on a client.
     */
    public static final String SET_COOKIE_HEADER = "Set-Cookie";

    private ServerCookieHelper() {
    }

    public static <T> RawHttpResponse<T> setCookie(RawHttpResponse<T> response, HttpCookie cookie) {
        return setCookie(response, cookie, null, null);
    }

    public static <T> RawHttpResponse<T> setCookie(RawHttpResponse<T> response, HttpCookie cookie,
                                                   @Nullable SameSite sameSite) {
        return setCookie(response, cookie, sameSite, null);
    }

    public static <T> RawHttpResponse<T> setCookie(RawHttpResponse<T> response, HttpCookie cookie,
                                                   @Nullable SameSite sameSite,
                                                   @Nullable Object extension) {
        return response.withHeaders(setCookie(response.getHeaders(), cookie, sameSite, extension));
    }

    public static <T> RawHttpResponse<T> setCookies(RawHttpResponse<T> response, List<HttpCookie> cookies) {
        return setCookies(response, cookies, null, null);
    }

    public static <T> RawHttpResponse<T> setCookies(RawHttpResponse<T> response, List<HttpCookie> cookies,
                                                    @Nullable SameSite sameSite) {
        return setCookies(response, cookies, sameSite, null);
    }

    public static <T> RawHttpResponse<T> setCookies(RawHttpResponse<T> response, List<HttpCookie> cookies,
                                                    @Nullable SameSite sameSite,
                                                    @Nullable Object extension) {
        return response.withHeaders(setCookies(response.getHeaders(), cookies, sameSite, extension));
    }

    public static RawHttpHeaders setCookies(RawHttpHeaders headers, List<HttpCookie> cookies) {
        return setCookies(headers, cookies, null, null);
    }

    public static RawHttpHeaders setCookies(RawHttpHeaders headers, List<HttpCookie> cookies,
                                            @Nullable SameSite sameSite) {
        return setCookies(headers, cookies, sameSite, null);
    }

    public static RawHttpHeaders setCookies(RawHttpHeaders headers, List<HttpCookie> cookies,
                                            @Nullable SameSite sameSite,
                                            @Nullable Object extension) {
        return setCookies(RawHttpHeaders.newBuilder(headers), cookies, sameSite, extension).build();
    }

    public static RawHttpHeaders.Builder setCookies(RawHttpHeaders.Builder headers,
                                                    List<HttpCookie> cookies) {
        return setCookies(headers, cookies, null, null);
    }

    public static RawHttpHeaders.Builder setCookies(RawHttpHeaders.Builder headers,
                                                    List<HttpCookie> cookies,
                                                    @Nullable SameSite sameSite) {
        return setCookies(headers, cookies, sameSite, null);
    }

    public static RawHttpHeaders.Builder setCookies(RawHttpHeaders.Builder headers,
                                                    List<HttpCookie> cookies,
                                                    @Nullable SameSite sameSite,
                                                    @Nullable Object extension) {
        for (HttpCookie cookie : cookies) {
            headers.with(SET_COOKIE_HEADER, setCookieHeaderValue(cookie, sameSite, extension));
        }
        return headers;
    }

    public static RawHttpHeaders setCookie(RawHttpHeaders headers, HttpCookie cookie) {
        return setCookie(headers, cookie, null);
    }

    public static RawHttpHeaders setCookie(RawHttpHeaders headers, HttpCookie cookie,
                                           @Nullable SameSite sameSite) {
        return setCookie(headers, cookie, sameSite, null);
    }

    public static RawHttpHeaders setCookie(RawHttpHeaders headers, HttpCookie cookie,
                                           @Nullable SameSite sameSite,
                                           @Nullable Object extension) {
        return setCookie(RawHttpHeaders.newBuilder(headers), cookie, sameSite, extension).build();
    }

    public static RawHttpHeaders.Builder setCookie(RawHttpHeaders.Builder headers, HttpCookie cookie) {
        return setCookie(headers, cookie, null, null);
    }

    public static RawHttpHeaders.Builder setCookie(RawHttpHeaders.Builder headers, HttpCookie cookie,
                                                   @Nullable SameSite sameSite) {
        return setCookie(headers, cookie, sameSite, null);
    }

    public static RawHttpHeaders.Builder setCookie(RawHttpHeaders.Builder headers, HttpCookie cookie,
                                                   @Nullable SameSite sameSite, @Nullable Object extension) {
        return headers.with(SET_COOKIE_HEADER, setCookieHeaderValue(cookie, sameSite, extension));
    }

    /**
     * Compute the value of the "Set-Cookie" header to represent the given cookie.
     *
     * @param cookie the cookie
     * @return the value of the "Set-Cookie" header
     */
    public static String setCookieHeaderValue(HttpCookie cookie) {
        return setCookieHeaderValue(cookie, null, null);
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
    public static String setCookieHeaderValue(HttpCookie cookie,
                                              @Nullable SameSite sameSite) {
        return setCookieHeaderValue(cookie, sameSite, null);
    }

    /**
     * Compute the value of the "Set-Cookie" header to represent the given cookie,
     * with an optional {@link SameSite} attribute and extension.
     *
     * @param cookie    the cookie
     * @param sameSite  attribute for the cookie (given separately as {@link HttpCookie} does not currently
     *                  support it
     * @param extension cookie extension
     * @return the value of the "Set-Cookie" header
     */
    public static String setCookieHeaderValue(HttpCookie cookie,
                                              @Nullable SameSite sameSite,
                                              @Nullable Object extension) {
        StringBuilder builder = new StringBuilder(cookie.getName());
        builder.append("=\"").append(cookie.getValue()).append('"');
        List<String> attributes = attributesForSetCookie(cookie, sameSite, extension);
        for (String attribute : attributes) {
            builder.append("; ").append(attribute);
        }
        return builder.toString();
    }

    /**
     * Read the cookies sent by a HTTP client in the given request.
     *
     * @param request that may contain cookies
     * @return the cookies sent by the client
     */
    public static List<HttpCookie> readClientCookies(RawHttpRequest request) {
        return readClientCookies(request.getHeaders());
    }

    /**
     * Read the cookies sent by a HTTP client within the given headers.
     *
     * @param headers from a HTTP request
     * @return the cookies sent by the client
     */
    public static List<HttpCookie> readClientCookies(RawHttpHeaders headers) {
        return headers.getFirst(COOKIE_HEADER)
                .map(ServerCookieHelper::readClientCookies)
                .orElse(Collections.emptyList());
    }

    /**
     * Create a List with the attributes for the cookie as specified in RFC-6265 and draft-west-first-party-cookies-07
     *
     * <pre>
     *         cookie-av  = expires-av / max-age-av / domain-av /
     *                      path-av / secure-av / httponly-av /
     *                      samesite-av / extension-av
     * </pre>
     *
     * @param cookie    cookie
     * @param sameSite  attribute
     * @param extension cookie extension
     * @return list of attributes for the cookie
     */
    private static List<String> attributesForSetCookie(HttpCookie cookie,
                                                       @Nullable SameSite sameSite,
                                                       @Nullable Object extension) {
        List<String> result = new ArrayList<>(6);
        addAttributeIfNotEmpty(result, "Max-Age", cookie.getMaxAge() < 0 ? null : cookie.getMaxAge());
        addAttributeIfNotEmpty(result, "Domain", cookie.getDomain());
        addAttributeIfNotEmpty(result, "Path", cookie.getPath());
        addAttributeIfTrue(result, "Secure", cookie.isHttpOnly());
        addAttributeIfTrue(result, "HttpOnly", cookie.isHttpOnly());
        addAttributeIfNotEmpty(result, "SameSite", sameSite);
        if (extension != null) {
            String extensionText = extension.toString();
            if (!extensionText.isEmpty()) {
                result.add(extensionText);
            }
        }
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

    private static List<HttpCookie> readClientCookies(String value) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        List<HttpCookie> result = new ArrayList<>();

        String[] cookiePairs = value.split(";");
        for (String cookiePair : cookiePairs) {
            String[] pair = cookiePair.split("=");
            if (pair.length == 2) {
                try {
                    result.add(new HttpCookie(pair[0].trim(), unquoted(pair[1])));
                } catch (IllegalArgumentException e) {
                    // ignore invalid cookie
                }
            }
        }
        return result;
    }

    private static String unquoted(String s) {
        if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 2);
        }
        return s;
    }
}
