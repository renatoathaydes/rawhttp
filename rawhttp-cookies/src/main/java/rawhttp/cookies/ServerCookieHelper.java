package rawhttp.cookies;

import rawhttp.core.RawHttpHeaders;
import rawhttp.core.RawHttpRequest;
import rawhttp.core.RawHttpResponse;

import javax.annotation.Nullable;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static <T> RawHttpResponse<T> withCookie(RawHttpResponse<T> response, HttpCookie cookie) {
        return response.withHeaders(withCookie(response.getHeaders(), cookie));
    }

    public static <T> RawHttpResponse<T> withCookies(RawHttpResponse<T> response, List<HttpCookie> cookies) {
        return response.withHeaders(withCookies(response.getHeaders(), cookies));
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

    /**
     * Read the cookies sent by a HTTP client in the given request.
     *
     * @param request that may contain cookies
     * @return the cookies sent by the client
     */
    public static List<HttpCookie> readCookies(RawHttpRequest request) {
        return readCookies(request.getHeaders());
    }

    /**
     * Read the cookies sent by a HTTP client within the given headers.
     *
     * @param headers from a HTTP request
     * @return the cookies sent by the client
     */
    public static List<HttpCookie> readCookies(RawHttpHeaders headers) {
        return headers.get(COOKIE_HEADER).stream()
                .flatMap(ServerCookieHelper::readClientCookies)
                .collect(Collectors.toList());
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

    private static Stream<HttpCookie> readClientCookies(String value) {
        if (value == null || value.trim().isEmpty()) return Stream.of();
        List<HttpCookie> result = new ArrayList<>();

        String[] cookiePairs = value.split(";");
        for (String cookiePair : cookiePairs) {
            String[] pair = cookiePair.split("=");
            if (pair.length == 2) {
                result.add(new HttpCookie(pair[0].trim(), unquoted(pair[1])));
            }
        }
        return result.stream();
    }

    private static String unquoted(String s) {
        if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 2);
        }
        return s;
    }
}
