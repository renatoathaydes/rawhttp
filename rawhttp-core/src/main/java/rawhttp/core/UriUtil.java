package rawhttp.core;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class UriUtil {

    public static String concatPaths(@Nullable String p1, String p2) {
        if (p1 == null || p1.isEmpty()) {
            return p2;
        }
        if (p1.endsWith("/")) {
            if (p2.startsWith("/")) {
                return p1 + p2.substring(1);
            } else {
                return p1 + p2;
            }
        } else {
            if (p2.startsWith("/")) {
                return p1 + p2;
            } else {
                return p1 + "/" + p2;
            }
        }
    }

    /**
     * Get a new URI based on the given URI, but with the host replaced with the given host.
     * <p>
     * The new host may include a port using the "host:port" syntax.
     * <p>
     * The new URI never maintains the old URI's port, even if the port is omitted from the new host String.
     *
     * @param uri  original URI
     * @param host to use in the returned URI
     * @return a new URI with the host replaced
     */
    public static URI withHost(URI uri, String host) {
        return with(uri, null, null, host, null, null, null);
    }

    public static URI withPath(URI uri, String path) {
        return with(uri, null, null, null, path, null, null);
    }

    private static URI with(URI uri, @Nullable String scheme, @Nullable String userInfo, @Nullable String host,
                            @Nullable String path, @Nullable String query, @Nullable String fragment) {
        StringBuilder builder = new StringBuilder(uri.toString().length());
        builder.append(scheme == null
                ? uri.getScheme() == null
                ? "http" : uri.getScheme()
                : scheme);

        builder.append("://");

        String ui = userInfo == null
                ? uri.getUserInfo()
                : userInfo;
        if (ui != null) {
            builder.append(ui).append('@');
        }

        builder.append(host == null ? uri.getHost() : host);

        String p = path == null
                ? uri.getRawPath()
                : path;
        if (p != null && !p.isEmpty()) {
            if (!p.startsWith("/")) {
                builder.append('/');
            }
            builder.append(p);
        }

        String q = query == null
                ? uri.getRawQuery()
                : query;
        if (q != null) {
            builder.append('?').append(q);
        }

        String f = fragment == null
                ? uri.getRawFragment()
                : fragment;
        if (f != null) {
            builder.append('#').append(f);
        }

        try {
            return new URI(builder.toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid host format" + Optional.ofNullable(
                    e.getMessage()).map(s -> ": " + s).orElse(""));
        }
    }

}
