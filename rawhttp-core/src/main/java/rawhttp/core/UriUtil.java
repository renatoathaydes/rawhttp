package rawhttp.core;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Utility static methods to handle {@link URI} instances and related concepts.
 */
public class UriUtil {

    /**
     * Concatenate two paths.
     * <p>
     * Use this method instead of String concatenation to avoid missing or double slashes between the
     * two paths.
     *
     * @param p1 first path
     * @param p2 second path
     * @return result of concatenating the paths
     */
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
     * Get a new URI based on the given URI, but with the scheme replaced with the given scheme.
     *
     * @param uri    original URI
     * @param scheme to use in the returned URI
     * @return a new URI with the scheme replaced
     */
    public static URI withScheme(URI uri, String scheme) {
        return with(uri, scheme, null, null, null, null, null);
    }

    /**
     * Get a new URI based on the given URI, but with the userInfo replaced with the given userInfo.
     *
     * @param uri      original URI
     * @param userInfo to use in the returned URI
     * @return a new URI with the userInfo replaced
     */
    public static URI withUserInfo(URI uri, String userInfo) {
        return with(uri, null, userInfo, null, null, null, null);
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

    /**
     * Get a new URI based on the given URI, but with the port replaced with the given port.
     *
     * @param uri  original URI
     * @param port to use in the returned URI
     * @return a new URI with the port replaced
     */
    public static URI withPort(URI uri, int port) {
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Cannot replace port in URI that does not have a host: " + uri);
        }
        int idx = host.indexOf(':');
        if (idx > 0) {
            host = host.substring(0, idx);
        }
        return with(uri, null, null, host + ":" + port, null, null, null);
    }

    /**
     * Get a new URI based on the given URI, but with the path replaced with the given path.
     *
     * @param uri  original URI
     * @param path to use in the returned URI
     * @return a new URI with the path replaced
     */
    public static URI withPath(URI uri, String path) {
        return with(uri, null, null, null, path, null, null);
    }

    /**
     * Get a new URI based on the given URI, but with the query replaced with the given query.
     *
     * @param uri   original URI
     * @param query to use in the returned URI
     * @return a new URI with the query replaced
     */
    public static URI withQuery(URI uri, String query) {
        return with(uri, null, null, null, null, query, null);
    }

    /**
     * Get a new URI based on the given URI, but with the fragment replaced with the given fragment.
     *
     * @param uri      original URI
     * @param fragment to use in the returned URI
     * @return a new URI with the fragment replaced
     */
    public static URI withFragment(URI uri, String fragment) {
        return with(uri, null, null, null, null, null, fragment);
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
        if (ui != null && !ui.isEmpty()) {
            builder.append(ui).append('@');
        }

        String h = host == null ? uri.getHost() : host;
        if (h != null) {
            builder.append(h);
        }

        // do not change the port if a host was given that contains a port
        if (uri.getPort() > 0 && !(host != null && host.contains(":"))) {
            builder.append(':').append(uri.getPort());
        }

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
        if (q != null && !q.isEmpty()) {
            builder.append('?').append(q);
        }

        String f = fragment == null
                ? uri.getRawFragment()
                : fragment;
        if (f != null && !f.isEmpty()) {
            builder.append('#').append(f);
        }

        try {
            return new URI(builder.toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid host format" + Optional.ofNullable(
                    e.getMessage()).map(s -> ": " + s).orElse(""));
        }
    }

    /**
     * @return a {@link URI} builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Check if the given URI has the default port for its scheme.
     * <p>
     * This method only consider the "http" and "https" schemes.
     *
     * @param uri http URI
     * @return true if the scheme is "http" and the port is 80 or if the scheme is "https" and the port 443,
     * false otherwise.
     */
    public static boolean hasDefaultPort(URI uri) {
        return (uri.getPort() < 0)
                || ((uri.getPort() == 80) && ("http".equalsIgnoreCase(uri.getScheme())))
                || ((uri.getPort() == 443) && ("https".equalsIgnoreCase(uri.getScheme())));
    }

    /**
     * Builder of {@link URI} instances.
     */
    public static final class Builder {
        @Nullable
        String scheme, userInfo, host, path, query, fragment;

        private Builder() {
        }

        public Builder withScheme(@Nullable String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder withUserInfo(@Nullable String userInfo) {
            this.userInfo = userInfo;
            return this;
        }

        public Builder withHost(@Nullable String host) {
            this.host = host;
            return this;
        }

        public Builder withPath(@Nullable String path) {
            this.path = path;
            return this;
        }

        public Builder withQuery(@Nullable String query) {
            this.query = query;
            return this;
        }

        public Builder withFragment(@Nullable String fragment) {
            this.fragment = fragment;
            return this;
        }

        public URI build() {
            return with(URI.create(""), scheme, userInfo, host, path, query, fragment);
        }
    }

}
